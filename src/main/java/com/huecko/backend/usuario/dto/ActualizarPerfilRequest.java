package com.huecko.backend.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * PATCH parcial: los campos nulos se dejan como están. Por eso no llevan
 * @NotBlank — solo se valida el formato de lo que sí venga.
 */
public record ActualizarPerfilRequest(
        @Size(min = 1, max = 120, message = "El nombre debe tener entre 1 y 120 caracteres")
        String nombre,

        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 180, message = "El correo no puede superar los 180 caracteres")
        String email
) {
}
