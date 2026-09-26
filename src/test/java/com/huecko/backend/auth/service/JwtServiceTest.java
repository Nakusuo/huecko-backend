package com.huecko.backend.auth.service;

import com.huecko.backend.postgres.entity.Usuario.RolSistema;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El rol viaja en el token: de él depende que /api/admin/** abra o dé 403. */
class JwtServiceTest {

    private static final String SECRETO = "clave-de-pruebas-huecko-con-mas-de-32-caracteres";

    private final JwtService jwtService = new JwtService(SECRETO, 60, new MockEnvironment());

    @Test
    @DisplayName("el rol ADMIN sobrevive al viaje de ida y vuelta")
    void rolAdmin() {
        UUID id = UUID.randomUUID();
        String token = jwtService.generar(id, "admin@huecko.com", "Admin", RolSistema.ADMIN);

        UsuarioAutenticado usuario = jwtService.validar(token).orElseThrow();

        assertThat(usuario.id()).isEqualTo(id);
        assertThat(usuario.rolSistema()).isEqualTo(RolSistema.ADMIN);
        assertThat(usuario.esAdmin()).isTrue();
    }

    @Test
    @DisplayName("una cuenta normal no es admin")
    void rolUsuario() {
        String token = jwtService.generar(UUID.randomUUID(), "ana@huecko.com", "Ana", RolSistema.USUARIO);

        assertThat(jwtService.validar(token).orElseThrow().esAdmin()).isFalse();
    }

    @Test
    @DisplayName("un token emitido antes de existir el rol cuenta como usuario normal")
    void tokenSinRol() {
        Instant ahora = Instant.now();
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "ana@huecko.com")
                .claim("nombre", "Ana")
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(10, ChronoUnit.MINUTES)))
                .signWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtService.validar(token).orElseThrow().rolSistema()).isEqualTo(RolSistema.USUARIO);
    }
}
