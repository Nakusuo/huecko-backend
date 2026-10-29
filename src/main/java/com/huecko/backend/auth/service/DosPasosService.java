package com.huecko.backend.auth.service;

import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.UnauthorizedException;
import com.huecko.backend.postgres.entity.CodigoRespaldo;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.CodigoRespaldoRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Verificación en dos pasos con una app de códigos (TOTP).
 *
 * 1. `iniciar`: genera el secreto y la dirección del QR. Aún no cuenta.
 * 2. `activar`: con el primer código correcto queda activa y se entregan los
 *    códigos de respaldo, que solo se ven esa vez.
 * 3. Desde entonces, el login pide el código después de la contraseña
 *    (`AuthService.completarDosPasos`).
 */
@Service
@RequiredArgsConstructor
public class DosPasosService {

    /** Lo que muestra la app junto al correo. */
    static final String EMISOR = "Huecko";
    static final int CANTIDAD_CODIGOS_RESPALDO = 10;
    /** Sin 0/O, 1/I ni L para que se puedan copiar a mano sin dudas. */
    private static final String ALFABETO_RESPALDO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LARGO_RESPALDO = 8;
    private static final SecureRandom AZAR = new SecureRandom();

    public static final String CODIGO_INCORRECTO = "El código no es correcto o ya se usó. Prueba con el que muestra ahora la app.";

    private final UsuarioRepository usuarioRepository;
    private final CodigoRespaldoRepository codigoRespaldoRepository;
    private final CifradoSecretos cifrado;
    private final PasswordEncoder passwordEncoder;

    public record Estado(boolean activo, long codigosRespaldoRestantes, Instant activadoEn) {
    }

    /** `secreto` para teclearlo a mano si no se puede escanear el QR. */
    public record Configuracion(String secreto, String uri) {
    }

    @Transactional(readOnly = true)
    public Estado estado(UUID usuarioId) {
        Usuario usuario = cargar(usuarioId);
        long restantes = usuario.isDosPasosActivo()
                ? codigoRespaldoRepository.countByUsuarioIdAndUsadoEnIsNull(usuarioId)
                : 0;
        return new Estado(usuario.isDosPasosActivo(), restantes, usuario.getDosPasosActivadoEn());
    }

    @Transactional
    public Configuracion iniciar(UUID usuarioId) {
        Usuario usuario = cargar(usuarioId);
        if (usuario.isDosPasosActivo()) {
            throw new BusinessException("La verificación en dos pasos ya está activada.");
        }
        // Empezar otra vez descarta el secreto anterior a medio configurar.
        String secreto = Totp.nuevoSecreto();
        usuario.setDosPasosSecreto(cifrado.cifrar(secreto));
        usuario.setDosPasosUltimoPaso(null);
        return new Configuracion(secreto, Totp.uri(EMISOR, usuario.getEmail(), secreto));
    }

    /** Devuelve los códigos de respaldo en claro: es la única vez que se pueden ver. */
    @Transactional
    public List<String> activar(UUID usuarioId, String codigo, Instant ahora) {
        Usuario usuario = cargar(usuarioId);
        if (usuario.isDosPasosActivo()) {
            throw new BusinessException("La verificación en dos pasos ya está activada.");
        }
        if (usuario.getDosPasosSecreto() == null) {
            throw new BusinessException("Primero escanea el código QR.");
        }
        if (!codigoDeLaApp(usuario, codigo, ahora)) {
            throw new BusinessException(CODIGO_INCORRECTO);
        }
        usuario.setDosPasosActivo(true);
        usuario.setDosPasosActivadoEn(ahora);
        return nuevosCodigosRespaldo(usuarioId);
    }

    /** Otros códigos de respaldo; los anteriores dejan de valer. Pide un código de la app. */
    @Transactional
    public List<String> regenerarCodigos(UUID usuarioId, String codigo, Instant ahora) {
        Usuario usuario = cargarActivo(usuarioId);
        if (!codigoDeLaApp(usuario, codigo, ahora)) {
            throw new BusinessException(CODIGO_INCORRECTO);
        }
        return nuevosCodigosRespaldo(usuarioId);
    }

    /**
     * Pide la contraseña y un código (de la app o de respaldo): quien encuentre
     * la sesión abierta en otro equipo no puede quitar la protección.
     */
    @Transactional
    public void desactivar(UUID usuarioId, String password, String codigo, Instant ahora) {
        Usuario usuario = cargarActivo(usuarioId);
        if (password == null || !passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new BusinessException("La contraseña no es correcta.");
        }
        if (!comprobar(usuario, codigo, ahora)) {
            throw new BusinessException(CODIGO_INCORRECTO);
        }
        usuario.setDosPasosActivo(false);
        usuario.setDosPasosSecreto(null);
        usuario.setDosPasosUltimoPaso(null);
        usuario.setDosPasosActivadoEn(null);
        codigoRespaldoRepository.borrarDelUsuario(usuarioId);
    }

    /**
     * Código del segundo paso del login: 6 dígitos de la app o uno de
     * respaldo, que queda gastado. Lo llama AuthService dentro de su
     * transacción, así que los cambios se guardan con ella.
     */
    public boolean comprobar(Usuario usuario, String codigo, Instant ahora) {
        if (codigo == null) {
            return false;
        }
        String limpio = codigo.replace(" ", "").replace("-", "").toUpperCase();
        if (limpio.matches("\\d{" + Totp.DIGITOS + "}")) {
            return codigoDeLaApp(usuario, limpio, ahora);
        }
        if (limpio.length() != LARGO_RESPALDO) {
            return false;
        }
        return codigoRespaldoRepository
                .findByUsuarioIdAndCodigoHashAndUsadoEnIsNull(usuario.getId(), hash(limpio))
                .map(respaldo -> {
                    respaldo.setUsadoEn(ahora);
                    return true;
                })
                .orElse(false);
    }

    private boolean codigoDeLaApp(Usuario usuario, String codigo, Instant ahora) {
        if (codigo == null || usuario.getDosPasosSecreto() == null) {
            return false;
        }
        String secreto = cifrado.descifrar(usuario.getDosPasosSecreto());
        OptionalLong paso = Totp.verificar(secreto, codigo.trim(), ahora, usuario.getDosPasosUltimoPaso());
        paso.ifPresent(usuario::setDosPasosUltimoPaso);
        return paso.isPresent();
    }

    private List<String> nuevosCodigosRespaldo(UUID usuarioId) {
        codigoRespaldoRepository.borrarDelUsuario(usuarioId);
        List<String> codigos = new ArrayList<>(CANTIDAD_CODIGOS_RESPALDO);
        List<CodigoRespaldo> filas = new ArrayList<>(CANTIDAD_CODIGOS_RESPALDO);
        for (int i = 0; i < CANTIDAD_CODIGOS_RESPALDO; i++) {
            String codigo = codigoAlAzar();
            codigos.add(codigo.substring(0, 4) + "-" + codigo.substring(4));
            filas.add(CodigoRespaldo.builder()
                    .id(UUID.randomUUID())
                    .usuarioId(usuarioId)
                    .codigoHash(hash(codigo))
                    .build());
        }
        codigoRespaldoRepository.saveAll(filas);
        return codigos;
    }

    private static String codigoAlAzar() {
        StringBuilder codigo = new StringBuilder(LARGO_RESPALDO);
        for (int i = 0; i < LARGO_RESPALDO; i++) {
            codigo.append(ALFABETO_RESPALDO.charAt(AZAR.nextInt(ALFABETO_RESPALDO.length())));
        }
        return codigo.toString();
    }

    /** SHA-256 basta: son 40 bits al azar, de un solo uso y con límite de intentos. */
    static String hash(String codigo) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    private Usuario cargarActivo(UUID usuarioId) {
        Usuario usuario = cargar(usuarioId);
        if (!usuario.isDosPasosActivo()) {
            throw new BusinessException("La verificación en dos pasos no está activada.");
        }
        return usuario;
    }

    private Usuario cargar(UUID usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Tu cuenta ya no existe. Inicia sesión de nuevo."));
    }
}
