package com.huecko.backend.ia;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Mantiene despierta la IA y sabe cómo está.
 *
 * En el plan gratuito de Render la IA se duerme tras unos minutos sin
 * peticiones y tarda alrededor de un minuto en volver. La API no la espera
 * (3 s, ver ClienteIA), así que sin esto casi todo salía por reglas
 * (`REGLAS_POR_FALLO`). Se la llama al arrancar y cada 10 minutos mientras la
 * API está despierta; cuando la API duerme, su proceso se congela y deja de
 * llamar, así que la IA duerme con ella.
 *
 * Va en su propio hilo: cada llamada puede tardar un minuto, y en el hilo de
 * las tareas programadas retrasaría el cierre de las votaciones.
 */
@Component
public class VigiaIA {

    private static final Logger log = LoggerFactory.getLogger(VigiaIA.class);
    private static final Duration INTERVALO = Duration.ofMinutes(10);
    /** Un arranque en frío de la IA en el plan gratuito, con margen. */
    private static final Duration ESPERA_DESPERTAR = Duration.ofSeconds(90);

    public enum Estado { DESACTIVADA, SIN_COMPROBAR, ACTIVA, SIN_GEMINI, SIN_RESPUESTA }

    public record Comprobacion(Estado estado, Instant comprobadoEn, Long latenciaMs) {
    }

    private final RestClient http;
    private final boolean enUso;
    private volatile Comprobacion ultimo;
    private ScheduledExecutorService hilo;

    @Autowired
    public VigiaIA(RestClient.Builder builder,
                   @Value("${huecko.ia.url:http://localhost:8000}") String url,
                   @Value("${huecko.imprevistos.evaluador:reglas}") String evaluador,
                   @Value("${huecko.ia.recomendaciones:false}") boolean recomendaciones) {
        this(builder.clone().requestFactory(conEsperaLarga()), url,
                "ia".equalsIgnoreCase(evaluador) || recomendaciones);
    }

    /** Sin tocar la fábrica de peticiones: así las pruebas pueden poner la suya. */
    VigiaIA(RestClient.Builder builder, String url, boolean enUso) {
        this.http = builder.baseUrl(url).build();
        this.enUso = enUso;
        this.ultimo = new Comprobacion(enUso ? Estado.SIN_COMPROBAR : Estado.DESACTIVADA, null, null);
    }

    private static SimpleClientHttpRequestFactory conEsperaLarga() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(15));
        fabrica.setReadTimeout(ESPERA_DESPERTAR);
        return fabrica;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void empezar() {
        if (!enUso) {
            return;
        }
        hilo = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "vigia-ia");
            t.setDaemon(true);
            return t;
        });
        hilo.scheduleWithFixedDelay(this::comprobar, 0, INTERVALO.toMinutes(), TimeUnit.MINUTES);
    }

    @PreDestroy
    public void parar() {
        if (hilo != null) {
            hilo.shutdownNow();
        }
    }

    /** Una comprobación, que de paso la despierta. Nunca lanza. */
    void comprobar() {
        if (!enUso) {
            return;
        }
        long inicio = System.nanoTime();
        Estado estado;
        try {
            Map<?, ?> salud = http.get().uri("/salud").retrieve().body(Map.class);
            estado = salud != null && Boolean.TRUE.equals(salud.get("gemini")) ? Estado.ACTIVA : Estado.SIN_GEMINI;
        } catch (RuntimeException ex) {
            log.warn("La IA no responde ({}): las funciones con IA usan reglas.", ex.getClass().getSimpleName());
            estado = Estado.SIN_RESPUESTA;
        }
        long ms = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
        ultimo = new Comprobacion(estado, Instant.now(), estado == Estado.SIN_RESPUESTA ? null : ms);
    }

    public Comprobacion ultimo() {
        return ultimo;
    }
}
