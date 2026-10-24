package com.huecko.backend.ia.service;

import com.huecko.backend.mongo.document.LlamadaIA;
import com.huecko.backend.mongo.repository.LlamadaIARepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Mide cada llamada a la IA que pide la persona y la anota (LlamadaIA). Solo
 * las que llegan a salir hacia la IA: lo rechazado antes (sin cupo, foto
 * inválida, grupo ajeno) no es uso de la IA.
 *
 * Anotar nunca rompe la función: si Mongo falla, se avisa en el log y la
 * persona recibe su respuesta igual.
 */
@Component
public class RegistroUsoIA {

    private static final Logger log = LoggerFactory.getLogger(RegistroUsoIA.class);

    /** Lo que se guarda: un mes basta para ver tendencias sin acumular para siempre. */
    static final Duration RETENCION = Duration.ofDays(30);

    private final LlamadaIARepository llamadas;
    private final Clock reloj;

    @Autowired
    public RegistroUsoIA(LlamadaIARepository llamadas) {
        this(llamadas, Clock.systemUTC());
    }

    RegistroUsoIA(LlamadaIARepository llamadas, Clock reloj) {
        this.llamadas = llamadas;
        this.reloj = reloj;
    }

    /** Ejecuta la llamada y la anota como buena o fallida; lo que lance, lo relanza tal cual. */
    public <T> T medir(LlamadaIA.Funcion funcion, Supplier<T> llamada) {
        long inicio = System.nanoTime();
        boolean ok = false;
        try {
            T resultado = llamada.get();
            ok = true;
            return resultado;
        } finally {
            anotar(funcion, ok, Duration.ofNanos(System.nanoTime() - inicio).toMillis());
        }
    }

    private void anotar(LlamadaIA.Funcion funcion, boolean ok, long ms) {
        Instant ahora = reloj.instant();
        try {
            llamadas.save(LlamadaIA.builder()
                    .funcion(funcion)
                    .ok(ok)
                    .duracionMs(ms)
                    .ocurridaEn(ahora)
                    .purgarEn(ahora.plus(RETENCION))
                    .build());
        } catch (RuntimeException ex) {
            log.warn("No se pudo anotar el uso de la IA ({}): {}", funcion, ex.getClass().getSimpleName());
        }
    }
}
