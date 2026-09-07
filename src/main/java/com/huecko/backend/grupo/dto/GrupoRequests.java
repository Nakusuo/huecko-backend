package com.huecko.backend.grupo.dto;

import com.huecko.backend.postgres.entity.MiembroGrupo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Los cuatro cuerpos de entrada del módulo de grupos, juntos por ser records de
 * tres líneas que solo se usan aquí. Separarlos en cuatro archivos añadiría
 * ruido sin añadir información.
 */
public final class GrupoRequests {

    private GrupoRequests() {
    }

    /** HU-05: crear el grupo. El umbral es opcional y cae a unanimidad. */
    public record Crear(
            @NotBlank(message = "El nombre del grupo es obligatorio")
            @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
            String nombre,

            @Size(max = 400, message = "La descripción no puede superar los 400 caracteres")
            String descripcion,

            /**
             * RF-06. Se limita por abajo a 50 porque un umbral menor deja de
             * describir al grupo: sugeriría planes donde la mayoría no puede ir.
             */
            @Min(value = 50, message = "El umbral debe estar entre 50 y 100")
            @Max(value = 100, message = "El umbral debe estar entre 50 y 100")
            Integer umbralDisponibilidad
    ) {
    }

    /** Todos los campos son opcionales: es un PATCH, solo se toca lo que llega. */
    public record Actualizar(
            @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
            String nombre,

            @Size(max = 400, message = "La descripción no puede superar los 400 caracteres")
            String descripcion,

            @Min(value = 50, message = "El umbral debe estar entre 50 y 100")
            @Max(value = 100, message = "El umbral debe estar entre 50 y 100")
            Integer umbralDisponibilidad
    ) {
    }

    public record Unirse(
            @NotBlank(message = "El código de invitación es obligatorio")
            @Size(max = 20, message = "El código no puede superar los 20 caracteres")
            String codigoInvitacion
    ) {
    }

    /** HU-14: marcar a alguien como imprescindible, o pasarle la organización. */
    public record ActualizarMiembro(
            MiembroGrupo.Rol rol,
            Boolean esImprescindible
    ) {
    }
}
