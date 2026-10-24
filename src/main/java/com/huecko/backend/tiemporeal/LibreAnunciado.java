package com.huecko.backend.tiemporeal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Alguien avisó de que está libre. Va a todos sus grupos en vivo; la hora y la
 * nota las escribió esa persona, no salen de su horario (RNF-02).
 */
public record LibreAnunciado(List<UUID> grupoIds, UUID usuarioId, String nombre, Instant hasta, String nota) {
}
