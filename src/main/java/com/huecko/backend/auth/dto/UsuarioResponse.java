package com.huecko.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.huecko.backend.postgres.entity.Usuario;

import java.time.Instant;

/**
 * Usuario tal como lo consume el frontend (`AuthUser` en types/auth.types.ts).
 *
 * OJO con `creado_en`: es el único campo del contrato en snake_case. El resto
 * de la API va en camelCase, así que se marca aquí en vez de cambiar la
 * estrategia global de nombres de Jackson.
 */
public record UsuarioResponse(
        String id,
        String nombre,
        String email,
        @JsonProperty("creado_en") Instant creadoEn
) {
    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId().toString(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getCreadoEn());
    }
}
