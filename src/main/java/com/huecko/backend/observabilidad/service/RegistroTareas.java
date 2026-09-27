package com.huecko.backend.observabilidad.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lo que hizo cada tarea programada desde que arrancó el backend.
 *
 * Vive en memoria a propósito: responde «¿está corriendo ahora?», no guarda un
 * histórico. Tras un reinicio empieza vacío, y el panel lo explica.
 */
@Component
public class RegistroTareas {

    /** Foto de una tarea. Los campos de la última ejecución son nulos si aún no corrió. */
    public record Ejecuciones(long total, Instant ultimaEjecucion, Long duracionMs, int ultimosProcesados,
                              int ultimosFallidos, long fallidosTotales, String ultimoError, Instant ultimoErrorEn) {

        static final Ejecuciones NINGUNA = new Ejecuciones(0, null, null, 0, 0, 0, null, null);
    }

    private final Map<Tarea, Ejecuciones> estado = new EnumMap<>(Tarea.class);

    /**
     * Anota una pasada. `ultimoError` es el mensaje del último fallo de esta
     * pasada, o nulo si todo fue bien.
     */
    public synchronized void registrar(Tarea tarea, Instant inicio, Duration duracion, int procesados,
                                       int fallidos, String ultimoError) {
        Ejecuciones previa = estado.getOrDefault(tarea, Ejecuciones.NINGUNA);
        estado.put(tarea, new Ejecuciones(
                previa.total() + 1,
                inicio,
                duracion.toMillis(),
                procesados,
                fallidos,
                previa.fallidosTotales() + fallidos,
                ultimoError != null ? ultimoError : previa.ultimoError(),
                ultimoError != null ? inicio : previa.ultimoErrorEn()));
    }

    public synchronized Ejecuciones de(Tarea tarea) {
        return Optional.ofNullable(estado.get(tarea)).orElse(Ejecuciones.NINGUNA);
    }
}
