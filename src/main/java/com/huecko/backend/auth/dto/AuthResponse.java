package com.huecko.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Respuesta de /auth/login y /auth/register: `{ token, user }`.
 *
 * Si la cuenta tiene la verificación en dos pasos, el login responde en su
 * lugar `{ requiereDosPasos: true, desafio }`: sin token ni usuario hasta que
 * llegue el código a /auth/dos-pasos.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(String token, UsuarioResponse user, Boolean requiereDosPasos, String desafio) {

    public AuthResponse(String token, UsuarioResponse user) {
        this(token, user, null, null);
    }

    public static AuthResponse pedirCodigo(String desafio) {
        return new AuthResponse(null, null, true, desafio);
    }
}
