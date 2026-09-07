package com.huecko.backend.plan.event;

import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.VentanaPlan;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Se publica cuando una votación termina, tanto si confirma como si cancela.
 *
 * Es un evento de dominio y no una llamada directa al notificador porque
 * {@code PlanService.cerrar} corre dentro de una transacción: avisar allí
 * mismo sacaría el mensaje ANTES del commit, y si el commit fallara el grupo
 * habría recibido una hora confirmada que no existe en la base. Quien escucha
 * espera al commit.
 *
 * Lleva copiados los datos que necesita el aviso en vez de la entidad: cuando
 * el oyente se ejecuta, la transacción ya cerró y una entidad con relaciones
 * perezosas fallaría al leerlas.
 */
public record PlanCerradoEvent(
        UUID grupoId,
        UUID planId,
        String titulo,
        String lugar,
        Plan.Estado estado,
        LocalDate fecha,
        LocalTime horaInicio,
        LocalTime horaFin
) {

    public static PlanCerradoEvent de(Plan plan) {
        VentanaPlan ganadora = plan.getVentanaConfirmada();
        return new PlanCerradoEvent(
                plan.getGrupo().getId(),
                plan.getId(),
                plan.getTitulo(),
                plan.getLugar(),
                plan.getEstado(),
                ganadora != null ? ganadora.getFecha() : null,
                ganadora != null ? ganadora.getHoraInicio() : null,
                ganadora != null ? ganadora.getHoraFin() : null);
    }

    public boolean confirmado() {
        return estado == Plan.Estado.CONFIRMADO;
    }
}
