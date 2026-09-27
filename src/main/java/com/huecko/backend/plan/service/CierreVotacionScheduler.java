package com.huecko.backend.plan.service;

import com.huecko.backend.observabilidad.service.RegistroFallos;
import com.huecko.backend.observabilidad.service.RegistroTareas;
import com.huecko.backend.observabilidad.service.Tarea;
import com.huecko.backend.postgres.entity.Plan;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * RF-10: cierra las votaciones cuyo plazo ya vencio y confirma la ganadora.
 *
 * Es un barrido periodico y no un temporizador por plan a proposito. Un
 * temporizador vive en la memoria de UNA instancia: si el proceso se reinicia
 * -y en un free tier se reinicia solo (RNF-09)- los planes programados se
 * quedan abiertos para siempre. El barrido, en cambio, recupera al arrancar
 * todo lo que venciera mientras estaba caido.
 *
 * `fixedDelay` y no `fixedRate`: si un barrido tardara mas de un minuto,
 * fixedRate encadenaria ejecuciones solapadas sobre los mismos planes.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "huecko.planes.cierre-automatico", havingValue = "true", matchIfMissing = true)
public class CierreVotacionScheduler {

    private static final Logger log = LoggerFactory.getLogger(CierreVotacionScheduler.class);

    private final PlanService planService;
    private final RegistroTareas registroTareas;
    private final RegistroFallos registroFallos;

    @Scheduled(fixedDelayString = "${huecko.planes.intervalo-cierre-ms:60000}",
               initialDelayString = "${huecko.planes.retardo-inicial-ms:10000}")
    public void cerrarVotacionesVencidas() {
        Instant inicio = Instant.now();
        int fallidos = 0;
        String ultimoError = null;

        List<Plan> vencidos = planService.vencidosSinCerrar(inicio);
        if (!vencidos.isEmpty()) {
            log.info("Cerrando {} votacion(es) con el plazo vencido", vencidos.size());
        }
        for (Plan plan : vencidos) {
            try {
                // Una transaccion por plan: que uno falle no debe dejar sin
                // cerrar a los demas del mismo barrido.
                planService.cerrarPorPlazoVencido(plan.getId());
            } catch (RuntimeException ex) {
                fallidos++;
                ultimoError = "Plan " + plan.getId() + ": " + ex.getClass().getSimpleName();
                log.error("No se pudo cerrar la votacion del plan {}", plan.getId(), ex);
                registroFallos.registrarTarea(Tarea.CIERRE_PLANES, ex);
            }
        }

        // También las pasadas vacías: son la prueba de que la tarea sigue viva.
        registroTareas.registrar(Tarea.CIERRE_PLANES, inicio, Duration.between(inicio, Instant.now()),
                vencidos.size() - fallidos, fallidos, ultimoError);
    }
}
