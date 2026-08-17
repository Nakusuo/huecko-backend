package com.huecko.backend.horario.dto;

import com.huecko.backend.mongo.document.BloqueHorario;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO de entrada (RF-01, RF-04). La validación de que diaSemana/fecha
 * correspondan al tipo se hace en el service, no aquí, porque depende
 * de la combinación de dos campos.
 */
public record BloqueHorarioRequest(
        @NotNull BloqueHorario.Tipo tipo,
        Integer diaSemana,
        LocalDate fecha,
        @NotNull LocalTime horaInicio,
        @NotNull LocalTime horaFin,
        String etiqueta
) {
}
