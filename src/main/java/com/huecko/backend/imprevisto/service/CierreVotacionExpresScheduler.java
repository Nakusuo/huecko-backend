package com.huecko.backend.imprevisto.service;

import com.huecko.backend.mongo.document.VotacionExpres;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    @Scheduled(fixedDelayString = "${huecko.imprevistos.intervalo-cierre-ms:30000}",
               initialDelayString = "${huecko.imprevistos.retardo-inicial-ms:15000}")
    public void cerrarVencidas() {
        List<VotacionExpres> vencidas = imprevistoService.vencidasSinCerrar(Instant.now());
        if (vencidas.isEmpty()) {
            return;
        }

        log.info("Cerrando {} votacion(es) expres con el plazo vencido", vencidas.size());
        for (VotacionExpres votacion : vencidas) {
            try {
                // Una por una: que falle el plan de una no debe dejar abiertas
                // las demás del mismo barrido.
                imprevistoService.cerrar(votacion.getId());
            } catch (RuntimeException ex) {
                log.error("No se pudo cerrar la votacion expres {}", votacion.getId(), ex);
            }
        }
    }
}
