package com.huecko.backend.libre.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * «Estoy libre». Sin `hasta`, el servidor usa lo que dice tu horario (hasta tu
 * siguiente clase de hoy).
 */
public record MarcarLibreRequest(
        Instant hasta,
        @Size(max = 60, message = "La nota puede tener hasta 60 caracteres")
        String nota
) {
}
