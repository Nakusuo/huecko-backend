package com.huecko.backend.observabilidad.dto;

import java.time.Instant;
import java.util.List;

/** Estado del backend para el panel: dependencias, tareas programadas y el proceso. */
public record SaludResponse(
        Estado estado,
        Instant generadoEn,
        List<Componente> componentes,
        List<TareaEstado> tareas,
        Aplicacion aplicacion
) {

    /** El peor gana: con una base caída el sistema está caído aunque lo demás vaya bien. */
    public enum Estado { OK, DEGRADADO, CAIDO }

    /** `latenciaMs` es nula si no se llegó a medir (el componente no respondió). */
    public record Componente(String clave, String nombre, Estado estado, Long latenciaMs, String detalle) {
    }

    /**
     * `atrasadas` = elementos que ya deberían haberse procesado y siguen ahí.
     * Con la tarea sana es siempre cero.
     */
    public record TareaEstado(String clave, String nombre, String descripcion, boolean activa, long intervaloMs,
                              Estado estado, long atrasadas, long ejecuciones, Instant ultimaEjecucion,
                              Long duracionMs, int ultimosProcesados, long fallidosTotales, String ultimoError,
                              Instant ultimoErrorEn) {
    }

    public record Aplicacion(String version, List<String> perfiles, Instant arranque, long segundosEncendida,
                             String java, long memoriaUsadaMb, long memoriaMaximaMb, String zonaHoraria) {
    }
}
