package com.huecko.backend.tiemporeal;

import java.time.Instant;
import java.util.UUID;

/**
 * Un recordatorio para el grupo: que el plan empieza en una hora
 * (`EMPIEZA_PRONTO`) o que su votación está por cerrar (`CIERRA_PRONTO`).
 *
 * @param cuando la hora de inicio del plan o el cierre de la votación
 */
public record Recordatorio(Tipo tipo, UUID grupoId, UUID planId, String titulo, String lugar, Instant cuando) {

    public enum Tipo { EMPIEZA_PRONTO, CIERRA_PRONTO }
}
