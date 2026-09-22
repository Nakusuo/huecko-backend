package com.huecko.backend.common.exception;

/**
 * El usuario está autenticado pero no puede tocar este recurso.
 * Se traduce a HTTP 403 (distinto del 401, que la UI usa para mandar al login).
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
