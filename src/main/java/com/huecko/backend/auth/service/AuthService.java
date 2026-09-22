package com.huecko.backend.auth.service;

import com.huecko.backend.auth.dto.AuthResponse;
import com.huecko.backend.auth.dto.LoginRequest;
import com.huecko.backend.auth.dto.RegisterRequest;
import com.huecko.backend.auth.dto.UsuarioResponse;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** Mismo texto para "no existe" y "contraseña mal": no se filtra qué correos hay registrados. */
    private static final String CREDENCIALES_INVALIDAS = "Credenciales incorrectas. Intenta de nuevo.";

    /**
     * BCrypt solo usa los primeros 72 bytes: dos contraseñas largas que
     * empiezan igual serían la misma. Se corta en el registro, antes de cifrar.
     */
    private static final int MAX_BYTES_PASSWORD = 72;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Hash de una contraseña al azar, generado con el mismo encoder la primera
     * vez que hace falta. Cuando el correo no existe se compara contra él
     * igualmente: sin eso la respuesta llegaba mucho más rápido y delataba qué
     * correos están registrados. No es `final` para que Lombok no lo pida en
     * el constructor.
     */
    private volatile String hashDeRelleno;

    @Transactional
    public AuthResponse registrar(RegisterRequest request) {
        String email = normalizar(request.email());

        if (request.password().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_PASSWORD) {
            throw new BusinessException("La contraseña es demasiado larga (máximo 72 caracteres).");
        }

        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("El correo ya está en uso. Intenta con otro.");
        }

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .nombre(request.nombre().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .build());

        return responder(usuario);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Ninguna cuenta puede tener una contraseña más larga (ver `registrar`), y
        // BCrypt rechaza con excepción las de más de 72 bytes.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_PASSWORD) {
            throw new BusinessException(CREDENCIALES_INVALIDAS);
        }

        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(normalizar(request.email()))
                .orElse(null);

        // Se compara siempre, exista o no el correo, para que el tiempo de
        // respuesta sea el mismo en los dos casos.
        String hash = usuario != null ? usuario.getPasswordHash() : hashDeRelleno();
        boolean coincide = passwordEncoder.matches(request.password(), hash);

        if (usuario == null || !coincide) {
            throw new BusinessException(CREDENCIALES_INVALIDAS);
        }

        return responder(usuario);
    }

    private String hashDeRelleno() {
        String hash = hashDeRelleno;
        if (hash == null) {
            hash = passwordEncoder.encode(UUID.randomUUID().toString());
            hashDeRelleno = hash;
        }
        return hash;
    }

    private AuthResponse responder(Usuario usuario) {
        String token = jwtService.generar(usuario.getId(), usuario.getEmail(), usuario.getNombre());
        return new AuthResponse(token, UsuarioResponse.from(usuario));
    }

    private String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
