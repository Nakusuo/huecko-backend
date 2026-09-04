package com.huecko.backend.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
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

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(
            @Value("${huecko.jwt.secret}") String secret,
            @Value("${huecko.jwt.expiration-minutes}") long expirationMinutes) {

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "huecko.jwt.secret debe tener al menos " + MIN_SECRET_BYTES + " caracteres; tiene " + bytes.length);
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMinutes = expirationMinutes;
    }

    public String generar(UUID usuarioId, String email, String nombre) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(usuarioId.toString())
                .claim("email", email)
                .claim("nombre", nombre)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
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

            return Optional.of(new UsuarioAutenticado(
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("nombre", String.class)));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
