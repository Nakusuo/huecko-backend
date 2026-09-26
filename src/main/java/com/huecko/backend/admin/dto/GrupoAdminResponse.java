package com.huecko.backend.admin.dto;

import com.huecko.backend.postgres.entity.Plan;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Un grupo visto desde el panel: quién lo organiza, cuánta gente tiene y cuánto
 * se mueve. Sin la lista de integrantes, sin sus horarios y sin el contenido de
 * los planes.
 *
 * `ultimaActividad` es nula si nunca pasó nada en él tras crearlo.
 */
public record GrupoAdminResponse(
        String id,
        String nombre,
        Instant creadoEn,
        List<String> organizadores,
        long miembros,
        int umbralDisponibilidad,
        long planes,
        Map<Plan.Estado, Long> planesPorEstado,
        long imprevistos,
        Instant ultimaActividad
) {
}
