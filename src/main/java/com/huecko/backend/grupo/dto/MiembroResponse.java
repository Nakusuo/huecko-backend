package com.huecko.backend.grupo.dto;

import com.huecko.backend.postgres.entity.MiembroGrupo;

import java.time.Instant;
import java.util.UUID;

/**
 * Un integrante tal como lo ve el resto del grupo.
 *
 * RNF-02: aquí no viaja nada del horario de la persona. Que alguien esté libre
 * o no se responde en el heatmap y de forma agregada; esta lista solo dice
 * quién está en el grupo y con qué papel.
 */
public record MiembroResponse(
        UUID usuarioId,
        String nombre,
        String email,
        MiembroGrupo.Rol rol,
        /** HU-14: si su ausencia debe tratarse como crítica. */
        boolean esImprescindible,
        /** Nulo si no tiene foto. La foto se pide aparte, con permiso (ver FotoPerfilService). */
        Instant fotoActualizadaEn
) {
    public static MiembroResponse from(MiembroGrupo miembro) {
        return new MiembroResponse(
                miembro.getUsuario().getId(),
                miembro.getUsuario().getNombre(),
                miembro.getUsuario().getEmail(),
                miembro.getRol(),
                miembro.isEsImprescindible(),
                miembro.getUsuario().getFotoActualizadaEn());
    }
}
