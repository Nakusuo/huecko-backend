package com.huecko.backend.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class PlanRequests {

    private PlanRequests() {
    }

    /** HU-08: proponer un plan con entre 2 y 5 ventanas de tiempo. */
    public record Crear(
            @NotBlank(message = "El título del plan es obligatorio")
            @Size(max = 120, message = "El título no puede superar los 120 caracteres")
            String titulo,

            @Size(max = 200, message = "El lugar no puede superar los 200 caracteres")
            String lugar,

            @NotNull(message = "El plazo de votación es obligatorio")
            Instant plazoVotacion,

            /** HU-09: si es `false`, cada persona solo puede marcar una opción. */
            Boolean votosMultiples,

            /**
             * El mínimo de 2 y el máximo de 5 son de RF-08. Con una sola opción
             * no hay nada que votar, y con más de cinco la decisión se dispersa.
             */
            @NotEmpty(message = "Hay que proponer al menos 2 ventanas de tiempo")
            @Size(min = 2, max = 5, message = "Un plan se vota entre 2 y 5 ventanas de tiempo")
            @Valid
            List<Ventana> ventanas
    ) {
    }

    /**
     * Una opción de fecha y hora.
     *
     * Lleva fecha concreta y no día de la semana: el heatmap habla de rutinas
     * semanales, pero un plan ocurre un día determinado.
     */
    public record Ventana(
            @NotNull(message = "Cada ventana necesita una fecha")
            LocalDate fecha,

            @NotNull(message = "Cada ventana necesita una hora de inicio")
            LocalTime horaInicio,

            @NotNull(message = "Cada ventana necesita una hora de fin")
            LocalTime horaFin
    ) {
    }
}
