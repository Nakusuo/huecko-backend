package com.huecko.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.huecko.backend.postgres.entity.Usuario;

import java.time.Instant;
import java.util.List;

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
        @JsonProperty("creado_en") Instant creadoEn,
        Usuario.RolSistema rolSistema,
        String carrera,
        Integer ciclo,
        String bio,
        List<String> intereses,
        /** Nulo si no tiene foto; si no, sirve de versión para no pedirla dos veces. */
        Instant fotoActualizadaEn,
        /** Si al entrar se le pide el código de la app (verificación en dos pasos). */
        boolean dosPasosActivo
) {
    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId().toString(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getCreadoEn(),
                usuario.getRolSistema(),
                usuario.getCarrera(),
                usuario.getCiclo(),
                usuario.getBio(),
                List.copyOf(usuario.getIntereses() == null ? List.of() : usuario.getIntereses()),
                usuario.getFotoActualizadaEn(),
                usuario.isDosPasosActivo());
    }
}
