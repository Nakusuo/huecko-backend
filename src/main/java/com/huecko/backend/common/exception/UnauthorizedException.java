package com.huecko.backend.common.exception;

/**
 * La identidad del token ya no vale (por ejemplo, el usuario fue borrado).
 * Se traduce a HTTP 401, que el frontend usa para volver al login: con un 404
 * la persona se quedaba dentro de una sesión que ya no servía.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
