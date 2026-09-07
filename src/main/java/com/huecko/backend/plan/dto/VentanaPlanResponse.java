package com.huecko.backend.plan.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record VentanaPlanResponse(
        UUID id,
        LocalDate fecha,
        /** 1 = lunes ... 7 = domingo. Derivado de la fecha. */
        int diaSemana,
        LocalTime horaInicio,
        LocalTime horaFin,
        /** Foto de la disponibilidad al proponer el plan (RF-08), no un dato vivo. */
        int disponibilidadPorcentaje,
        int votos,
        /** Quienes votaron esta opcion. El grupo ve quien apoya que. */
        List<UUID> votantes,
        boolean votadaPorMi
) {
}
