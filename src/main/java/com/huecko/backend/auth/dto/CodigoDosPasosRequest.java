package com.huecko.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Un código de la app de autenticación (o de respaldo, donde se admite). */
public record CodigoDosPasosRequest(
        @NotBlank(message = "Escribe el código")
        @Size(max = 20, message = "El código no es válido")
        String codigo
) {
}
