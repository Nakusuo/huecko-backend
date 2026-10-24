package com.huecko.backend.ia.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Borrador de plan para rellenar el formulario de «Proponer plan». No se
 * guarda: la persona lo revisa y crea el plan por la vía normal.
 *
 * @param categoria ESTUDIO, REUNION, COMIDA, DEPORTE, SALIDA u OTRO; solo orienta, no se guarda
 * @param lugar null si la persona no mencionó ninguno
 * @param ventanas de 0 a 3, cada una dentro de un hueco real del grupo y sin solaparse
 * @param plazoVotacion sugerido; siempre cierra antes de la primera opción
 */
public record BorradorPlanResponse(String titulo, String categoria, String lugar,
                                   List<Ventana> ventanas, Instant plazoVotacion) {

    /** @param disponibilidadPorcentaje del grupo libre en el peor momento del hueco */
    public record Ventana(LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, int disponibilidadPorcentaje) {
    }
}
