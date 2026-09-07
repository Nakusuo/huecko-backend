package com.huecko.backend.grupo.dto;

import com.huecko.backend.postgres.entity.Grupo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GrupoResponse(
        UUID id,
        String nombre,
        String descripcion,
        String codigoInvitacion,
        UUID creadoPor,
        int umbralDisponibilidad,
        Instant creadoEn,
        List<MiembroResponse> miembros
) {
    public static GrupoResponse from(Grupo grupo, List<MiembroResponse> miembros) {
        return new GrupoResponse(
                grupo.getId(),
                grupo.getNombre(),
                grupo.getDescripcion(),
                grupo.getCodigoInvitacion(),
                grupo.getCreadoPor().getId(),
                grupo.getUmbralDisponibilidad(),
                grupo.getCreadoEn(),
                miembros);
    }
}
