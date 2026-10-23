package com.huecko.backend.common.exception;

/**
 * Una dependencia opcional (hoy, la IA) no puede atender ahora. Se traduce a
 * HTTP 503: el cliente sabe que puede reintentar más tarde o seguir sin ella.
 */
public class ServicioNoDisponibleException extends RuntimeException {
    public ServicioNoDisponibleException(String message) {
        super(message);
    }
}
