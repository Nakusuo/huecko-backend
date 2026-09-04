package com.huecko.backend.horario.dto;

import com.huecko.backend.mongo.document.BloqueHorario;

import java.time.LocalDate;
import java.time.LocalTime;

public record BloqueHorarioResponse(
        String id,
        String usuarioId,
        BloqueHorario.Tipo tipo,
        Integer diaSemana,
        LocalDate fecha,
        LocalDate fechaFin,
        LocalTime horaInicio,
        LocalTime horaFin,
        String etiqueta,
        String categoria,
        String color,
        BloqueHorario.Fuente fuente,
        BloqueHorario.Estado estado
) {
    public static BloqueHorarioResponse from(BloqueHorario b) {
        return new BloqueHorarioResponse(
                b.getId(), b.getUsuarioId(), b.getTipo(), b.getDiaSemana(), b.getFecha(), b.getFechaFin(),
                b.getHoraInicio(), b.getHoraFin(), b.getEtiqueta(), b.getCategoria(), b.getColor(),
                b.getFuente(), b.getEstado()
        );
    }
}
