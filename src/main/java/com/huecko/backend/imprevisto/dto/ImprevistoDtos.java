package com.huecko.backend.imprevisto.dto;

import com.huecko.backend.mongo.document.VotacionExpres;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Peticiones y respuestas del Módulo 5 (HU-13 a HU-16). */
public final class ImprevistoDtos {

    private ImprevistoDtos() {
    }

    /** RF-15: el motivo es opcional, la ausencia no. */
    public record Reportar(
            @Size(max = 300, message = "El motivo no puede pasar de 300 caracteres")
            String motivo
    ) {
    }

    /** RF-17: votar en la votación exprés. */
    public record VotarExpres(
            @NotNull(message = "Hay que elegir una opción")
            VotacionExpres.Opcion opcion
    ) {
    }

    /**
     * Qué pasó al reportar la ausencia.
     *
     * Cuando no es crítica no hay votación (RF-19) y `votacion` viene nula:
     * el cliente distingue los dos caminos por ese campo, sin adivinar.
     */
    public record ResultadoReporte(
            VotacionExpres.Criticidad criticidad,
            String razon,
            /** REGLAS, IA o REGLAS_POR_FALLO. Ver EvaluadorCriticidad.Origen. */
            String origen,
            VotacionExpresResponse votacion
    ) {
    }

    public record VotacionExpresResponse(
            String id,
            String planId,
            String nombreReporta,
            String motivo,
            VotacionExpres.Criticidad criticidad,
            String razonCriticidad,
            String origenCriticidad,
            VotacionExpres.Estado estado,
            List<VotacionExpres.Opcion> opciones,
            /** Recuento por opción, para pintar la barra sin exponer quién votó qué. */
            Map<VotacionExpres.Opcion, Integer> recuento,
            /** Lo que votó quien pregunta, o nulo si aún no votó. */
            VotacionExpres.Opcion miVoto,
            int votosEmitidos,
            int miembrosDelGrupo,
            Instant expiraEn,
            VotacionExpres.Opcion resultado,
            boolean resultadoPorDefecto
    ) {

        public static VotacionExpresResponse from(VotacionExpres v, String usuarioId) {
            Map<VotacionExpres.Opcion, Integer> recuento = new java.util.EnumMap<>(VotacionExpres.Opcion.class);
            for (VotacionExpres.Opcion opcion : VotacionExpres.Opcion.values()) {
                recuento.put(opcion, 0);
            }
            v.getVotos().values().forEach(op -> recuento.merge(op, 1, Integer::sum));

            return new VotacionExpresResponse(
                    v.getId(),
                    v.getPlanId(),
                    v.getNombreReporta(),
                    v.getMotivo(),
                    v.getCriticidad(),
                    v.getRazonCriticidad(),
                    v.getOrigenCriticidad(),
                    v.getEstado(),
                    List.of(VotacionExpres.Opcion.values()),
                    recuento,
                    v.getVotos().get(usuarioId),
                    v.getVotos().size(),
                    v.getMiembrosDelGrupo(),
                    v.getExpiraEn(),
                    v.getResultado(),
                    v.isResultadoPorDefecto());
        }
    }
}
