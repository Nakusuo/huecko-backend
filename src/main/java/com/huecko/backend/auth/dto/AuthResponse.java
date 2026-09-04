package com.huecko.backend.auth.dto;

/** Respuesta de /auth/login y /auth/register: `{ token, user }`. */
public record AuthResponse(String token, UsuarioResponse user) {
}
