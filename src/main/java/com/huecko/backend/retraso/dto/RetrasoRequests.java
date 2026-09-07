package com.huecko.backend.retraso.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Peticiones del Módulo 4 (HU-11). */
public final class RetrasoRequests {

    private RetrasoRequests() {
    }

    /**
     * RF-12: avisar de un retraso con los minutos estimados.
     *
     * El mínimo es 1: «llego con 0 minutos de retraso» no es un aviso, y
     * dejarlo pasar llenaría la fila de puntualidad de gente que llega a su
     * hora. El máximo son 8 horas — por encima de eso ya no es un retraso,
     * es una ausencia, y eso es el Módulo 5.
     */
    public record Reportar(
            @Min(value = 1, message = "El retraso debe ser de al menos 1 minuto")
            @Max(value = 480, message = "Más de 8 horas no es un retraso: repórtalo como imprevisto")
            int minutosEstimados
    ) {
    }
}
