package com.huecko.backend.album.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComentarRequest(
        @NotBlank(message = "Escribe algo antes de enviarlo")
        @Size(max = 300, message = "El comentario puede tener hasta 300 caracteres")
        String texto
) {
}
