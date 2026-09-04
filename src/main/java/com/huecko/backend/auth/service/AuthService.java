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

@Service
@RequiredArgsConstructor
public class AuthService {

    /** Mismo texto para "no existe" y "contraseña mal": no se filtra qué correos hay registrados. */
    private static final String CREDENCIALES_INVALIDAS = "Credenciales incorrectas. Intenta de nuevo.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse registrar(RegisterRequest request) {
        String email = normalizar(request.email());

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
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(normalizar(request.email()))
                .orElseThrow(() -> new BusinessException(CREDENCIALES_INVALIDAS));

        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new BusinessException(CREDENCIALES_INVALIDAS);
        }

        return responder(usuario);
    }

    private AuthResponse responder(Usuario usuario) {
        String token = jwtService.generar(usuario.getId(), usuario.getEmail(), usuario.getNombre());
        return new AuthResponse(token, UsuarioResponse.from(usuario));
    }

    private String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
