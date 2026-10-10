package com.huecko.backend.common.exception;

/** Se superó un límite de intentos. Se traduce a HTTP 429. */
public class DemasiadosIntentosException extends RuntimeException {
    public DemasiadosIntentosException(String message) {
        super(message);
    }
}
