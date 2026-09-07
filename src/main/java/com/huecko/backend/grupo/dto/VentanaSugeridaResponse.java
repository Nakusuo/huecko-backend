package com.huecko.backend.grupo.dto;

import java.time.LocalTime;

/**
 * Un tramo continuo de horas que cumplen el umbral (RF-06). Es lo que RF-08
 * ofrece al organizador cuando propone un plan, de ahí que traiga ya un `id`
 * estable con el que el frontend puede referenciarla sin inventarse uno.
 */
public record VentanaSugeridaResponse(
        String id,
        int diaSemana,
        LocalTime horaInicio,
        LocalTime horaFin,
        /** El porcentaje del PEOR momento del tramo, no el promedio: es el que se garantiza. */
        int disponibilidadPorcentaje,
        int miembrosDisponibles,
        int totalMiembros
) {
}
