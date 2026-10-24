package com.huecko.backend.usuario.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * PUT del perfil social: lo que ve el resto de tus grupos. Reemplaza todo, y
 * un campo nulo o vacío lo borra; así «quitar mi carrera» no necesita un
 * valor especial.
 */
public record PerfilSocialRequest(
        @Size(max = 80, message = "La carrera no puede superar los 80 caracteres")
        String carrera,

        @Min(value = 1, message = "El ciclo va del 1 al 14")
        @Max(value = 14, message = "El ciclo va del 1 al 14")
        Integer ciclo,

        @Size(max = 160, message = "La frase no puede superar los 160 caracteres")
        String bio,

        @Size(max = PerfilSocialRequest.MAX_INTERESES, message = "Como mucho " + PerfilSocialRequest.MAX_INTERESES + " intereses")
        List<
                @Size(max = 24, message = "Cada interés puede tener hasta 24 caracteres")
                // Sin comas: se guardan todos juntos separados por comas.
                @Pattern(regexp = "[\\p{L}\\p{N} .+#&'-]*", message = "Un interés solo puede llevar letras, números y espacios")
                String> intereses
) {
    public static final int MAX_INTERESES = 8;
}
