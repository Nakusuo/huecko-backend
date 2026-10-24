package com.huecko.backend.muro;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Una página del muro del grupo, de lo más nuevo a lo más antiguo.
 *
 * @param siguiente para pedir las anteriores (`?antes=`); nulo si no hay más
 */
public record MuroResponse(List<Entrada> entradas, Instant siguiente) {

    public enum Tipo { MIEMBRO_UNIDO, PLAN_PROPUESTO, PLAN_CONFIRMADO, PLAN_CANCELADO, FOTOS_SUBIDAS, LLEGARON }

    /**
     * Lo que pasó. Según el tipo van unos campos u otros: un plan lleva su
     * título (y su fecha si se confirmó); unas fotos, cuántas y las primeras
     * cuatro para la vista previa; unas llegadas, quiénes.
     */
    public record Entrada(Tipo tipo, Instant ocurridoEn, Persona actor, UUID planId, String titulo, LocalDate fecha,
                          LocalTime horaInicio, Integer cantidad, List<UUID> fotoIds, List<Persona> personas) {
    }

    public record Persona(UUID usuarioId, String nombre, Instant fotoActualizadaEn) {
    }
}
