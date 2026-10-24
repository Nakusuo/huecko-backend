package com.huecko.backend.grupo.dto;

import com.huecko.backend.postgres.entity.Grupo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GrupoResponse(
        UUID id,
        String nombre,
        String descripcion,
        UUID creadoPor,
        int umbralDisponibilidad,
        Instant creadoEn,
        List<MiembroResponse> miembros,
        /**
         * Solo para quien organiza: es quien decide a quién dárselo. `null`
         * para el resto, o si está desactivado.
         */
        String codigoInvitacion
) {
    public static GrupoResponse from(Grupo grupo, List<MiembroResponse> miembros, boolean verCodigo) {
        return new GrupoResponse(
                grupo.getId(),
                grupo.getNombre(),
                grupo.getDescripcion(),
                grupo.getCreadoPor().getId(),
                grupo.getUmbralDisponibilidad(),
                grupo.getCreadoEn(),
                miembros,
                verCodigo ? grupo.getCodigoInvitacion() : null);
    }
}
