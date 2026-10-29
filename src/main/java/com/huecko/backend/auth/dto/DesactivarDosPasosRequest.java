package com.huecko.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Quitar la verificación en dos pasos pide las dos cosas: la contraseña y un código. */
public record DesactivarDosPasosRequest(
        @NotBlank(message = "Escribe tu contraseña")
        @Size(max = 200)
        String password,

        @NotBlank(message = "Escribe el código")
        @Size(max = 20, message = "El código no es válido")
        String codigo
) {
}
