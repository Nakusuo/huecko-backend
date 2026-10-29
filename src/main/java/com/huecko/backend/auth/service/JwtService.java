package com.huecko.backend.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.huecko.backend.postgres.entity.Usuario.RolSistema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/** Emite y verifica los JWT de sesión (HS256). */
@Service
public class JwtService {

    /** HS256 exige una clave de al menos 256 bits. */
    private static final int MIN_SECRET_BYTES = 32;

    /** Prefijo de la clave de desarrollo que está publicada en el repositorio. */
    private static final String PREFIJO_CLAVE_DE_DESARROLLO = "cambia-esta-clave";

    /** Solo lo llevan los desafíos de dos pasos; un token de sesión no tiene `tipo`. */
    private static final String CLAIM_TIPO = "tipo";
    private static final String TIPO_DESAFIO = "dos-pasos";
    static final java.time.Duration DURACION_DESAFIO = java.time.Duration.ofMinutes(5);

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(
            @Value("${huecko.jwt.secret}") String secret,
            @Value("${huecko.jwt.expiration-minutes}") long expirationMinutes,
            Environment environment) {

        /* La clave de desarrollo está en application-dev.yml y en .env.example,
           a la vista de cualquiera. Con ella se puede firmar un token con el id
           de otra persona y entrar como ella. En producción no se arranca. */
        if (environment.acceptsProfiles(Profiles.of("prod"))
                && secret.startsWith(PREFIJO_CLAVE_DE_DESARROLLO)) {
            throw new IllegalStateException(
                    "HUECKO_JWT_SECRET sigue siendo la clave de desarrollo. Define una clave propia para producción.");
        }

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "huecko.jwt.secret debe tener al menos " + MIN_SECRET_BYTES + " caracteres; tiene " + bytes.length);
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMinutes = expirationMinutes;
    }

    /**
     * El rol va dentro del token para no consultar la base en cada petición.
     * Contrapartida: un cambio de rol se nota en el siguiente inicio de sesión.
     */
    public String generar(UUID usuarioId, String email, String nombre, RolSistema rolSistema) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(usuarioId.toString())
                .claim("email", email)
                .claim("nombre", nombre)
                .claim("rol", rolSistema.name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /**
     * Desafío de la verificación en dos pasos: prueba que la contraseña fue
     * correcta y vale unos minutos para mandar el código. No abre sesión
     * (`validar` lo rechaza).
     */
    public String generarDesafio(UUID usuarioId) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(usuarioId.toString())
                .claim(CLAIM_TIPO, TIPO_DESAFIO)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(DURACION_DESAFIO)))
                .signWith(key)
                .compact();
    }

    /** El usuario de un desafío vigente; vacío si caducó, está alterado o no es un desafío. */
    public Optional<UUID> validarDesafio(String desafio) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(desafio).getPayload();
            if (!TIPO_DESAFIO.equals(claims.get(CLAIM_TIPO, String.class))) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * Devuelve la identidad si el token es válido y no ha caducado.
     * Un token corrupto, caducado o firmado con otra clave devuelve vacío: es
     * un caso esperado (401), no una excepción que deba propagarse.
     */
    public Optional<UsuarioAutenticado> validar(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Un desafío de dos pasos está firmado con la misma clave, pero no
            // es una sesión: solo dice que la contraseña fue correcta.
            if (claims.get(CLAIM_TIPO) != null) {
                return Optional.empty();
            }

            return Optional.of(new UsuarioAutenticado(
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("nombre", String.class),
                    leerRol(claims.get("rol", String.class))));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * Cuándo caduca un token ya validado. La usa el tiempo real para cortar
     * las sesiones WebSocket cuyo token deja de valer (SesionesTiempoReal).
     */
    public Optional<Instant> caducidad(String token) {
        try {
            Date expiracion = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload().getExpiration();
            return Optional.ofNullable(expiracion).map(Date::toInstant);
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /** Los tokens emitidos antes de existir el rol no lo traen: son de cuentas normales. */
    private static RolSistema leerRol(String rol) {
        return RolSistema.ADMIN.name().equals(rol) ? RolSistema.ADMIN : RolSistema.USUARIO;
    }
}
