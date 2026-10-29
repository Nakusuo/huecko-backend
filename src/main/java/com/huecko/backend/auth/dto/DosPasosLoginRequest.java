package com.huecko.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Segundo paso del login: el desafío que dio /auth/login y el código de la app (o uno de respaldo). */
public record DosPasosLoginRequest(
        @NotBlank(message = "Falta el desafío del inicio de sesión")
        @Size(max = 1000)
        String desafio,

        @NotBlank(message = "Escribe el código")
        @Size(max = 20, message = "El código no es válido")
        String codigo
) {
}
