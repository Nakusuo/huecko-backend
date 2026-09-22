package com.huecko.backend.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * La zona horaria en la que viven los grupos.
 *
 * Las fechas de los planes y de los bloques puntuales son fechas "de calendario"
 * (sin zona), así que decidir qué día es hoy depende de dónde está la gente, no
 * de dónde corre el servidor. Con `LocalDate.now()` a secas, un servidor en UTC
 * pasaba al día siguiente a las 19:00 de Lima: el heatmap saltaba de semana y un
 * plan para esa misma noche se rechazaba por "fecha pasada".
 *
 * Se puede cambiar con la variable de entorno {@code HUECKO_ZONA_HORARIA}.
 */
public final class ZonaHoraria {

    public static final ZoneId ZONA = ZoneId.of(
            System.getenv().getOrDefault("HUECKO_ZONA_HORARIA", "America/Lima"));

    private ZonaHoraria() {
    }

    /** Hoy, en la zona de los grupos. */
    public static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }

    /** El instante real de una fecha y hora de calendario, leídas en la zona de los grupos. */
    public static Instant instante(LocalDate fecha, LocalTime hora) {
        return fecha.atTime(hora).atZone(ZONA).toInstant();
    }
}
