package com.huecko.backend.observabilidad.service;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MetricasServiceTest {

    private final SimpleMeterRegistry registro = new SimpleMeterRegistry();
    private final MetricasService metricas = new MetricasService(registro);

    /** Lo que haría Spring MVC al atender `veces` peticiones a una ruta. */
    private void peticiones(String metodo, String uri, String estado, int veces, long ms) {
        Timer timer = Timer.builder("http.server.requests")
                .tags("method", metodo, "uri", uri, "status", estado)
                .register(registro);
        for (int i = 0; i < veces; i++) {
            timer.record(Duration.ofMillis(ms));
        }
    }

    @Test
    @DisplayName("cada punto de la serie es lo que pasó en su intervalo, no el acumulado")
    void serieConDiferencias() {
        Instant t0 = Instant.parse("2026-10-28T12:00:00Z");
        peticiones("GET", "/api/grupos", "200", 5, 20);
        metricas.muestrear(t0);

        peticiones("GET", "/api/grupos", "200", 3, 40);
        peticiones("POST", "/api/planes", "500", 1, 100);
        metricas.muestrear(t0.plusSeconds(10));

        var serie = metricas.metricas().serie();
        assertThat(serie).hasSize(1);
        assertThat(serie.get(0).peticiones()).isEqualTo(4);
        assertThat(serie.get(0).errores()).isEqualTo(1);
        assertThat(serie.get(0).mediaMs()).isEqualTo(55.0);
    }

    @Test
    @DisplayName("el resumen separa errores del servidor (5xx) y del cliente (4xx)")
    void resumen() {
        peticiones("GET", "/api/grupos", "200", 8, 10);
        peticiones("GET", "/api/grupos/{id}", "404", 1, 10);
        peticiones("POST", "/api/planes", "500", 1, 10);

        var resumen = metricas.metricas().resumen();
        assertThat(resumen.peticiones()).isEqualTo(10);
        assertThat(resumen.errores()).isEqualTo(1);
        assertThat(resumen.erroresCliente()).isEqualTo(1);
        assertThat(resumen.porcentajeErrores()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("ni la salud ni el propio panel de métricas cuentan como tráfico")
    void ignoraRuido() {
        peticiones("GET", "/api/actuator/health", "200", 50, 1);
        peticiones("GET", "/api/admin/metricas", "200", 50, 1);
        peticiones("GET", "/api/grupos", "200", 2, 1);

        var respuesta = metricas.metricas();
        assertThat(respuesta.resumen().peticiones()).isEqualTo(2);
        assertThat(respuesta.rutas()).extracting(MetricasService.Ruta::uri).containsExactly("/api/grupos");
    }

    @Test
    @DisplayName("las rutas salen de la más a la menos usada, juntando sus códigos")
    void rutasOrdenadas() {
        peticiones("GET", "/api/grupos", "200", 2, 10);
        peticiones("POST", "/api/planes", "201", 5, 10);
        peticiones("POST", "/api/planes", "500", 1, 10);

        var rutas = metricas.metricas().rutas();
        assertThat(rutas.get(0).uri()).isEqualTo("/api/planes");
        assertThat(rutas.get(0).peticiones()).isEqualTo(6);
        assertThat(rutas.get(0).errores()).isEqualTo(1);
    }
}
