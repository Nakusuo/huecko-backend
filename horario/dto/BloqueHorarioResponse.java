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
        LocalTime horaInicio,
        LocalTime horaFin,
        String etiqueta,
        BloqueHorario.Fuente fuente,
        BloqueHorario.Estado estado
) {
    public static BloqueHorarioResponse from(BloqueHorario b) {
        return new BloqueHorarioResponse(
                b.getId(), b.getUsuarioId(), b.getTipo(), b.getDiaSemana(), b.getFecha(),
                b.getHoraInicio(), b.getHoraFin(), b.getEtiqueta(), b.getFuente(), b.getEstado()
        );
    }
}
