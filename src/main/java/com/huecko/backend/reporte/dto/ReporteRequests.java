package com.huecko.backend.reporte.dto;

import com.huecko.backend.mongo.document.EstadoRevision;
import com.huecko.backend.mongo.document.ReporteUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpos de las peticiones de reportes y fallos. Los límites evitan que un cliente llene la base. */
public final class ReporteRequests {

    private ReporteRequests() {
    }

    /** «Reportar un problema». `emailReportado` solo cuenta en CONDUCTA, y ahí es obligatorio. */
    public record Crear(
            @NotNull(message = "Indica el tipo de problema") ReporteUsuario.Tipo tipo,
            @NotBlank(message = "Describe el problema")
            @Size(min = 10, max = 1000, message = "La descripción debe tener entre 10 y 1000 caracteres")
            String descripcion,
            @Size(max = 300) String ruta,
            @Email(message = "El correo no tiene un formato válido") @Size(max = 180) String emailReportado
    ) {
    }

    /** Error de JavaScript que el frontend envía solo. */
    public record ErrorCliente(
            @NotBlank @Size(max = 120) String tipo,
            @NotBlank @Size(max = 2000) String mensaje,
            @Size(max = 8000) String traza,
            @Size(max = 300) String ruta
    ) {
    }

    public record CambiarEstado(@NotNull(message = "Indica el estado") EstadoRevision estado) {
    }

    public record Suspension(@NotNull(message = "Indica si se suspende o se reactiva") Boolean suspendido) {
    }
}
