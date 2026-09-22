package com.huecko.backend.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH parcial: los campos nulos se dejan como están. Por eso no llevan
 * @NotBlank — solo se valida el formato de lo que sí venga.
 */
public record ActualizarPerfilRequest(
        @Size(min = 1, max = 120, message = "El nombre debe tener entre 1 y 120 caracteres")
        // Un nombre de solo espacios pasaba y quedaba guardado vacío.
        @Pattern(regexp = "(?s).*\\S.*", message = "El nombre no puede estar vacío")
        String nombre,

        // `@Email` da por bueno "", y un correo vacío dejaba la cuenta sin poder entrar.
        @Pattern(regexp = "(?s).*\\S.*", message = "El correo no puede estar vacío")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 180, message = "El correo no puede superar los 180 caracteres")
        String email
) {
}
