package com.huecko.backend.observabilidad.service;

/**
 * Las tareas programadas del backend. Cada una sabe qué propiedad la enciende
 * y cada cuánto corre, para que el panel pueda decir si está viva sin que la
 * tarea tenga que describirse a sí misma.
 */
public enum Tarea {

    CIERRE_PLANES(
            "Cierre de votaciones de planes",
            "Cierra los planes cuyo plazo de votación venció y confirma la ventana ganadora (RF-10).",
            "huecko.planes.cierre-automatico",
            "huecko.planes.intervalo-cierre-ms", 60_000),

    CIERRE_VOTACIONES_EXPRES(
            "Cierre de votaciones exprés",
            "Cierra las votaciones de imprevistos vencidas y aplica su resultado al plan (RF-17).",
            "huecko.imprevistos.cierre-automatico",
            "huecko.imprevistos.intervalo-cierre-ms", 30_000);

    private final String nombre;
    private final String descripcion;
    private final String propiedadActiva;
    private final String propiedadIntervalo;
    private final long intervaloPorDefectoMs;

    Tarea(String nombre, String descripcion, String propiedadActiva, String propiedadIntervalo,
          long intervaloPorDefectoMs) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.propiedadActiva = propiedadActiva;
        this.propiedadIntervalo = propiedadIntervalo;
        this.intervaloPorDefectoMs = intervaloPorDefectoMs;
    }

    public String nombre() {
        return nombre;
    }

    public String descripcion() {
        return descripcion;
    }

    public String propiedadActiva() {
        return propiedadActiva;
    }

    public String propiedadIntervalo() {
        return propiedadIntervalo;
    }

    public long intervaloPorDefectoMs() {
        return intervaloPorDefectoMs;
    }
}
