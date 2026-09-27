package com.huecko.backend.imprevisto.service;

import com.huecko.backend.mongo.document.VotacionExpres;
import com.huecko.backend.observabilidad.service.RegistroFallos;
import com.huecko.backend.observabilidad.service.RegistroTareas;
import com.huecko.backend.observabilidad.service.Tarea;
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
 * RF-18: cierra las votaciones exprés con el plazo vencido y aplica el
 * resultado, por votos o por defecto.
 *
 * <b>Este barrido es el que cierra, no el índice TTL de Mongo.</b> El TTL solo
 * limpia documentos ya cerrados: si dejáramos que expiraran solos, RF-18 no
 * llegaría a ocurrir nunca porque el documento desaparecería antes de que
 * nadie aplicara nada al plan.
 *
 * Corre cada 30 s y no cada minuto como el de las votaciones normales: aquí
 * los plazos son cortos —una hora— y un retraso de un minuto en una ventana
 * así se nota mucho más.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "huecko.imprevistos.cierre-automatico",
                       havingValue = "true", matchIfMissing = true)
public class CierreVotacionExpresScheduler {

    private static final Logger log = LoggerFactory.getLogger(CierreVotacionExpresScheduler.class);

    private final ImprevistoService imprevistoService;
    private final RegistroTareas registroTareas;
    private final RegistroFallos registroFallos;

    @Scheduled(fixedDelayString = "${huecko.imprevistos.intervalo-cierre-ms:30000}",
               initialDelayString = "${huecko.imprevistos.retardo-inicial-ms:15000}")
    public void cerrarVencidas() {
        Instant inicio = Instant.now();
        int fallidos = 0;
        String ultimoError = null;

        List<VotacionExpres> vencidas = imprevistoService.vencidasSinCerrar(inicio);
        if (!vencidas.isEmpty()) {
            log.info("Cerrando {} votacion(es) expres con el plazo vencido", vencidas.size());
        }
        for (VotacionExpres votacion : vencidas) {
            try {
                // Una por una: que falle el plan de una no debe dejar abiertas
                // las demás del mismo barrido.
                imprevistoService.cerrar(votacion.getId());
            } catch (RuntimeException ex) {
                fallidos++;
                ultimoError = "Votación " + votacion.getId() + ": " + ex.getClass().getSimpleName();
                log.error("No se pudo cerrar la votacion expres {}", votacion.getId(), ex);
                registroFallos.registrarTarea(Tarea.CIERRE_VOTACIONES_EXPRES, ex);
            }
        }

        // También las pasadas vacías: son la prueba de que la tarea sigue viva.
        registroTareas.registrar(Tarea.CIERRE_VOTACIONES_EXPRES, inicio, Duration.between(inicio, Instant.now()),
                vencidas.size() - fallidos, fallidos, ultimoError);
    }
}
