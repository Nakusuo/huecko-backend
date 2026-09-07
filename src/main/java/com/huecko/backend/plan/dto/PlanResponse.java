package com.huecko.backend.plan.dto;

import com.huecko.backend.postgres.entity.Plan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        UUID grupoId,
        String titulo,
        String lugar,
        UUID creadoPor,
        Instant plazoVotacion,
        Plan.Estado estado,
        boolean votosMultiples,
        List<VentanaPlanResponse> ventanas,
        /** RF-10: la ganadora, una vez cerrada la votacion. Nula mientras siga abierta. */
        UUID ventanaConfirmadaId,
        /**
         * Si la votacion sigue aceptando votos AHORA. Se calcula en el servidor
         * y no en el cliente: el reloj del navegador puede ir desfasado, y con
         * el, la decision de mostrar o no el boton de votar.
         */
        boolean votacionAbierta,
        Instant creadoEn,
        Instant cerradoEn
) {
}
