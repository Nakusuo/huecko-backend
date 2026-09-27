package com.huecko.backend.plan.event;

import com.huecko.backend.postgres.entity.Plan;

import java.time.Instant;
import java.util.UUID;

/**
 * Se publica cuando un plan cambia de un modo que el resto del grupo tiene que
 * ver sin recargar: se propone, se reagenda o cambia su recuento de votos.
 *
 * Mismo motivo que {@link PlanCerradoEvent} para ser un evento y no una
 * llamada al notificador: todo esto ocurre dentro de una transacción, y el
 * aviso solo debe salir cuando ya está guardado. Un «hay plan nuevo» que llega
 * antes de un rollback manda al grupo a buscar un plan que no existe.
 *
 * Lleva los datos copiados, no la entidad, por las relaciones perezosas.
 */
public record PlanCambiadoEvent(
        Cambio cambio,
        UUID grupoId,
        UUID planId,
        String titulo,
        /** Quien lo originó, para que su propio cliente no se notifique a sí mismo. */
        UUID usuarioId,
        Instant plazoVotacion
) {

    public enum Cambio { PROPUESTO, REAGENDADO, VOTO_ACTUALIZADO }

    public static PlanCambiadoEvent de(Cambio cambio, Plan plan, UUID usuarioId) {
        return new PlanCambiadoEvent(
                cambio,
                plan.getGrupo().getId(),
                plan.getId(),
                plan.getTitulo(),
                usuarioId,
                plan.getPlazoVotacion());
    }
}
