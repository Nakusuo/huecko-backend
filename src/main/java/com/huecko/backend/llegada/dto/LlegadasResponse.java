package com.huecko.backend.llegada.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * «Llegué» de un plan visto por una persona. El servidor decide si se puede
 * marcar ahora (`estado`), para no depender del reloj del navegador.
 *
 * @param estado SIN_FECHA (no está confirmado), ANTES, ABIERTA o CERRADA
 */
public record LlegadasResponse(Estado estado, Instant abre, Instant cierra, boolean yaLlegue,
                               List<Persona> llegadas) {

    public enum Estado { SIN_FECHA, ANTES, ABIERTA, CERRADA }

    public record Persona(UUID usuarioId, String nombre, Instant fotoActualizadaEn, Instant llegoEn, boolean aTiempo) {
    }
}
