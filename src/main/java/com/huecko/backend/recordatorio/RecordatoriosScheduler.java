package com.huecko.backend.recordatorio;

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

/**
 * Barrido de recordatorios. Como el de cierre de votaciones, es periódico y no
 * un temporizador por plan: en el plan gratuito el proceso se reinicia solo, y
 * un temporizador en memoria se perdería.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "huecko.recordatorios.activos", havingValue = "true", matchIfMissing = true)
public class RecordatoriosScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecordatoriosScheduler.class);

    private final RecordatoriosService recordatoriosService;
    private final RegistroTareas registroTareas;
    private final RegistroFallos registroFallos;

    @Scheduled(fixedDelayString = "${huecko.recordatorios.intervalo-ms:300000}",
               initialDelayString = "${huecko.recordatorios.retardo-inicial-ms:20000}")
    public void enviar() {
        Instant inicio = Instant.now();
        try {
            int enviados = recordatoriosService.enviarPendientes();
            if (enviados > 0) {
                log.info("Enviados {} recordatorio(s)", enviados);
            }
            registroTareas.registrar(Tarea.RECORDATORIOS, inicio, Duration.between(inicio, Instant.now()),
                    enviados, 0, null);
        } catch (RuntimeException ex) {
            log.error("No se pudieron enviar los recordatorios", ex);
            registroFallos.registrarTarea(Tarea.RECORDATORIOS, ex);
            registroTareas.registrar(Tarea.RECORDATORIOS, inicio, Duration.between(inicio, Instant.now()),
                    0, 1, ex.getClass().getSimpleName());
        }
    }
}
