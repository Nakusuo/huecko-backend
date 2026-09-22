package com.huecko.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        // Tope amplio solo para no procesar textos enormes; el límite real de
        // 72 bytes se aplica en AuthService con el mismo mensaje de siempre.
        @Size(max = 200, message = "La contraseña es demasiado larga")
        String password
) {
}
