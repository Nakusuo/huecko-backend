package com.huecko.backend.observabilidad.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Métricas de la API para el panel de administración: cuántas peticiones
 * llegan, cuántas fallan y cuánto tardan, en vivo y por endpoint.
 *
 * Micrometer (Spring Boot Actuator) ya mide cada petición en el timer
 * `http.server.requests`, con su método, ruta y código. Aquí solo se leen esos
 * contadores cada 10 segundos y se guarda la diferencia, para dibujar la
 * última media hora. Todo en memoria: al reiniciar la API empieza de cero.
 */
@Service
public class MetricasService {

    static final Duration INTERVALO = Duration.ofSeconds(10);
    /** 180 × 10 s = 30 minutos de historia. */
    static final int PUNTOS = 180;
    private static final int MAX_RUTAS = 10;
    private static final String TIMER = "http.server.requests";
    private static final double MB = 1024.0 * 1024.0;

    private final MeterRegistry registro;
    private final Deque<Punto> serie = new ArrayDeque<>(PUNTOS);
    private Totales anteriores;

    public MetricasService(MeterRegistry registro) {
        this.registro = registro;
    }

    /** Un intervalo de 10 s: peticiones atendidas, cuántas con error 5xx y su tiempo medio. */
    public record Punto(Instant momento, long peticiones, long errores, long erroresCliente, double mediaMs) {
    }

    public record Ruta(String metodo, String uri, long peticiones, long errores, double mediaMs, double p95Ms,
                       double maxMs) {
    }

    public record Resumen(long peticiones, long errores, long erroresCliente, double porcentajeErrores,
                          double mediaMs, long peticionesUltimoMinuto) {
    }

    public record Jvm(double memoriaUsadaMb, double memoriaMaximaMb, int hilos, double cpu, Instant arranque) {
    }

    public record MetricasResponse(int intervaloSegundos, Resumen resumen, List<Punto> serie, List<Ruta> rutas,
                                   Jvm jvm) {
    }

    /** Sumas de todos los endpoints en un instante (contadores que solo crecen). */
    record Totales(long peticiones, long errores, long erroresCliente, double tiempoMs) {
    }

    @Scheduled(fixedRate = 10_000, initialDelay = 10_000)
    public void muestrear() {
        muestrear(Instant.now());
    }

    void muestrear(Instant ahora) {
        Totales actuales = totales();
        synchronized (serie) {
            if (anteriores != null) {
                long peticiones = Math.max(0, actuales.peticiones() - anteriores.peticiones());
                double tiempo = Math.max(0, actuales.tiempoMs() - anteriores.tiempoMs());
                if (serie.size() == PUNTOS) {
                    serie.pollFirst();
                }
                serie.addLast(new Punto(ahora, peticiones,
                        Math.max(0, actuales.errores() - anteriores.errores()),
                        Math.max(0, actuales.erroresCliente() - anteriores.erroresCliente()),
                        peticiones == 0 ? 0 : redondear(tiempo / peticiones)));
            }
            anteriores = actuales;
        }
    }

    public MetricasResponse metricas() {
        List<Timer> timers = timers();
        Totales totales = sumar(timers);
        List<Punto> puntos;
        synchronized (serie) {
            puntos = List.copyOf(serie);
        }
        long ultimoMinuto = puntos.stream()
                .skip(Math.max(0, puntos.size() - 6))
                .mapToLong(Punto::peticiones)
                .sum();
        Resumen resumen = new Resumen(totales.peticiones(), totales.errores(), totales.erroresCliente(),
                totales.peticiones() == 0 ? 0 : redondear(100.0 * totales.errores() / totales.peticiones()),
                totales.peticiones() == 0 ? 0 : redondear(totales.tiempoMs() / totales.peticiones()),
                ultimoMinuto);
        return new MetricasResponse((int) INTERVALO.toSeconds(), resumen, puntos, rutas(timers), jvm());
    }

    /** Los endpoints más usados. Una misma ruta junta todos sus códigos de respuesta. */
    private List<Ruta> rutas(List<Timer> timers) {
        record Clave(String metodo, String uri) {
        }
        Map<Clave, List<Timer>> porRuta = new LinkedHashMap<>();
        for (Timer t : timers) {
            porRuta.computeIfAbsent(new Clave(t.getId().getTag("method"), t.getId().getTag("uri")),
                    k -> new ArrayList<>()).add(t);
        }
        return porRuta.entrySet().stream()
                .map(e -> {
                    List<Timer> grupo = e.getValue();
                    long peticiones = grupo.stream().mapToLong(Timer::count).sum();
                    double tiempo = grupo.stream().mapToDouble(t -> t.totalTime(TimeUnit.MILLISECONDS)).sum();
                    return new Ruta(e.getKey().metodo(), e.getKey().uri(), peticiones,
                            grupo.stream().filter(MetricasService::esErrorServidor).mapToLong(Timer::count).sum(),
                            peticiones == 0 ? 0 : redondear(tiempo / peticiones),
                            redondear(grupo.stream().mapToDouble(MetricasService::p95).max().orElse(0)),
                            redondear(grupo.stream().mapToDouble(t -> t.max(TimeUnit.MILLISECONDS)).max().orElse(0)));
                })
                .filter(r -> r.peticiones() > 0)
                .sorted(Comparator.comparingLong(Ruta::peticiones).reversed())
                .limit(MAX_RUTAS)
                .toList();
    }

    private Jvm jvm() {
        Runtime runtime = Runtime.getRuntime();
        double cpu = registro.find("process.cpu.usage").gauge() != null
                ? registro.find("process.cpu.usage").gauge().value()
                : 0;
        return new Jvm(
                redondear((runtime.totalMemory() - runtime.freeMemory()) / MB),
                redondear(runtime.maxMemory() / MB),
                ManagementFactory.getThreadMXBean().getThreadCount(),
                Double.isNaN(cpu) ? 0 : redondear(cpu * 100),
                Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()));
    }

    private Totales totales() {
        return sumar(timers());
    }

    private static Totales sumar(List<Timer> timers) {
        long peticiones = 0;
        long errores = 0;
        long erroresCliente = 0;
        double tiempo = 0;
        for (Timer t : timers) {
            peticiones += t.count();
            tiempo += t.totalTime(TimeUnit.MILLISECONDS);
            if (esErrorServidor(t)) {
                errores += t.count();
            } else if (esErrorCliente(t)) {
                erroresCliente += t.count();
            }
        }
        return new Totales(peticiones, errores, erroresCliente, tiempo);
    }

    /**
     * Las peticiones de la API, sin las de salud ni las del propio panel de
     * métricas: con el panel abierto, su consulta cada pocos segundos taparía
     * el tráfico real.
     */
    private List<Timer> timers() {
        return registro.find(TIMER).timers().stream()
                .filter(t -> {
                    String uri = t.getId().getTag("uri");
                    return uri != null && uri.startsWith("/api/")
                            && !uri.startsWith("/api/actuator")
                            && !uri.equals("/api/admin/metricas");
                })
                .toList();
    }

    private static boolean esErrorServidor(Timer t) {
        String estado = t.getId().getTag("status");
        return estado != null && estado.startsWith("5");
    }

    private static boolean esErrorCliente(Timer t) {
        String estado = t.getId().getTag("status");
        return estado != null && estado.startsWith("4");
    }

    private static double p95(Timer t) {
        for (ValueAtPercentile v : t.takeSnapshot().percentileValues()) {
            if (Math.abs(v.percentile() - 0.95) < 1e-9) {
                return v.value(TimeUnit.MILLISECONDS);
            }
        }
        return 0;
    }

    private static double redondear(double valor) {
        return Math.round(valor * 10) / 10.0;
    }
}
