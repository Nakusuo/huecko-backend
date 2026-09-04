package com.huecko.backend.horario.dto;

import com.huecko.backend.mongo.document.BloqueHorario;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO de entrada (RF-01, RF-04). La validación de que diaSemana/fecha
 * correspondan al tipo se hace en el service, no aquí, porque depende
 * de la combinación de dos campos.
 */
public record BloqueHorarioRequest(
        @NotNull(message = "El tipo de bloque es obligatorio")
        BloqueHorario.Tipo tipo,

        @Min(value = 1, message = "diaSemana va de 1 (lunes) a 7 (domingo)")
        @Max(value = 7, message = "diaSemana va de 1 (lunes) a 7 (domingo)")
        Integer diaSemana,

        LocalDate fecha,

        LocalDate fechaFin,

        @NotNull(message = "La hora de inicio es obligatoria")
        LocalTime horaInicio,

        @NotNull(message = "La hora de fin es obligatoria")
        LocalTime horaFin,

        @Size(max = 200, message = "La etiqueta no puede superar los 200 caracteres")
        String etiqueta,

        @Size(max = 60, message = "La categoría no puede superar los 60 caracteres")
        String categoria,

        @Size(max = 30, message = "El color no puede superar los 30 caracteres")
        String color,

        /** Opcional. Si llega OCR, el bloque nace como BORRADOR (RF-03). */
        BloqueHorario.Fuente fuente
) {
}
