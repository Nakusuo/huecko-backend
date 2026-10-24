package com.huecko.backend.ia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class IaRequests {

    private IaRequests() {
    }

    /** Lo que cuenta la persona: «repaso de 2 horas el viernes en la tarde, en la biblioteca». */
    public record BorradorPlan(
            @NotBlank(message = "Cuéntale a Huecko qué plan quieres")
            @Size(min = 3, max = 500, message = "Describe el plan en 3 a 500 caracteres")
            String texto
    ) {
    }
}
