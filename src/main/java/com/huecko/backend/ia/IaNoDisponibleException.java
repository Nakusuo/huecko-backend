package com.huecko.backend.ia;

/** El servicio de IA no respondió a tiempo o respondió algo inservible. */
public class IaNoDisponibleException extends RuntimeException {

    public IaNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
