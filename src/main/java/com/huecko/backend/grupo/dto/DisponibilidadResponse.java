package com.huecko.backend.grupo.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Respuesta de `GET /api/grupos/{id}/disponibilidad` (HU-05, HU-06). */
public record DisponibilidadResponse(
        UUID grupoId,
        int umbral,
        int totalMiembros,
        /** Lunes de la semana calculada: los bloques puntuales dependen de la fecha. */
        LocalDate semanaDesde,
        LocalDate semanaHasta,
        int horaDesde,
        int horaHasta,
        List<CeldaDisponibilidadResponse> celdas,
        List<VentanaSugeridaResponse> ventanasSugeridas
) {
}
