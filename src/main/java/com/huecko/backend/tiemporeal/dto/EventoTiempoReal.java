package com.huecko.backend.tiemporeal.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Sobre común de todo lo que sale por WebSocket (RNF-05).
 *
 * El cliente conmuta por `tipo` y lee `datos`. Va un solo sobre para todos los
 * eventos, en vez de un DTO por cada uno, porque el frontend necesita un único
 * punto de entrada al canal: si cada evento tuviera su propia forma, habría que
 * abrir una suscripción por tipo y el orden entre ellas dejaría de estar
 * garantizado.
 *
 * `datos` no lleva nunca la etiqueta de un bloque de horario (RNF-02).
 */
public record EventoTiempoReal(
        Tipo tipo,
        UUID grupoId,
        Instant ocurridoEn,
        Map<String, Object> datos
) {

    /** Cada tipo corresponde a un requerimiento funcional concreto. */
    public enum Tipo {
        /** RF-11: la votación cerró y hay fecha/hora confirmada. */
        PLAN_CONFIRMADO,
        /** RF-13: alguien avisa que llegará tarde; el evento no cambia. */
        RETRASO_REPORTADO,
        /** RF-19: baja no crítica, solo se informa. */
        AUSENCIA_REPORTADA,
        /** RF-17: baja crítica, se abre votación exprés. */
        VOTACION_EXPRES_ABIERTA,
        /** RF-17 y RF-18: la votación exprés terminó, por votos o por defecto. */
        VOTACION_EXPRES_CERRADA
    }

    public static EventoTiempoReal de(Tipo tipo, UUID grupoId, Map<String, Object> datos) {
        return new EventoTiempoReal(tipo, grupoId, Instant.now(), Map.copyOf(datos));
    }
}
