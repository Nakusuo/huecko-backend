package com.huecko.backend.retraso.dto;

import com.huecko.backend.mongo.document.AlertaRetraso;

import java.time.Instant;

/** Una persona y cuánto tarda (RF-14). */
public record RetrasoResponse(
        String usuarioId,
        String nombreUsuario,
        int minutosEstimados,
        Instant reportadoEn,
        /** `true` si corrigió su estimación después del primer aviso. */
        boolean corregido
) {

    public static RetrasoResponse from(AlertaRetraso alerta) {
        return new RetrasoResponse(
                alerta.getUsuarioId(),
                alerta.getNombreUsuario(),
                alerta.getMinutosEstimados(),
                alerta.getCreadoEn(),
                alerta.getActualizadoEn() != null
                        && !alerta.getActualizadoEn().equals(alerta.getCreadoEn()));
    }
}
