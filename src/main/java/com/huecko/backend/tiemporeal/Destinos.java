package com.huecko.backend.tiemporeal;

import java.util.Optional;
import java.util.UUID;

/**
 * Los destinos STOMP que publica el backend, en un solo sitio.
 *
 * Construir y leer el destino con las mismas dos funciones evita el fallo
 * clásico de este tipo de canal: el emisor publica en `/topic/grupos/{id}` y el
 * suscriptor escucha `/topic/grupo/{id}`. No falla nada, simplemente no llega
 * nada, y no hay excepción que lo delate.
 */
public final class Destinos {

    /** Todo lo que ocurre dentro de un grupo viaja por aquí. */
    private static final String PREFIJO_GRUPO = "/topic/grupos/";

    private Destinos() {
    }

    public static String grupo(UUID grupoId) {
        return PREFIJO_GRUPO + grupoId;
    }

    /**
     * Devuelve el grupo al que apunta un destino, o vacío si el destino no es
     * de grupo o el identificador no es un UUID.
     *
     * Vacío no significa "adelante": el interceptor de suscripción trata todo
     * lo que no sea un destino de grupo reconocible como no autorizado.
     */
    public static Optional<UUID> grupoDe(String destino) {
        if (destino == null || !destino.startsWith(PREFIJO_GRUPO)) {
            return Optional.empty();
        }

        String resto = destino.substring(PREFIJO_GRUPO.length());
        // Sin barras adicionales: `/topic/grupos/{id}` y nada más. Un
        // `/topic/grupos/{id}/../otro` no puede colarse como si fuera el grupo.
        if (resto.isEmpty() || resto.indexOf('/') >= 0) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(resto));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
