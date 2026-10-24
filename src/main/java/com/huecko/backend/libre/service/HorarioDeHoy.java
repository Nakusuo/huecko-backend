package com.huecko.backend.libre.service;

import com.huecko.backend.mongo.document.BloqueHorario;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * El horario de alguien en un momento concreto, con las mismas reglas que el
 * heatmap (CalculadoraDisponibilidad): un recurrente cuenta su día de la
 * semana, uno puntual los días entre `fecha` y `fechaFin`, y una hora de fin
 * nula o anterior al inicio se lee como «hasta el final del día». Aquí se mira
 * al minuto, no por franjas de una hora.
 */
final class HorarioDeHoy {

    private HorarioDeHoy() {
    }

    static boolean aplicaEl(BloqueHorario bloque, LocalDate dia) {
        if (bloque.getTipo() == BloqueHorario.Tipo.RECURRENTE) {
            return Objects.equals(bloque.getDiaSemana(), dia.getDayOfWeek().getValue());
        }
        LocalDate inicio = bloque.getFecha();
        if (inicio == null) {
            return false;
        }
        LocalDate fin = bloque.getFechaFin() == null ? inicio : bloque.getFechaFin();
        return !dia.isBefore(inicio) && !dia.isAfter(fin);
    }

    /** Si en `momento` tiene algún bloque. */
    static boolean ocupadoA(List<BloqueHorario> bloques, LocalDateTime momento) {
        int minuto = momento.getHour() * 60 + momento.getMinute();
        return bloques.stream()
                .filter(b -> aplicaEl(b, momento.toLocalDate()))
                .anyMatch(b -> inicio(b) <= minuto && minuto < fin(b));
    }

    /** Cuándo empieza su siguiente bloque ese mismo día, o nulo si no le queda ninguno. */
    static LocalTime siguienteInicio(List<BloqueHorario> bloques, LocalDateTime momento) {
        int minuto = momento.getHour() * 60 + momento.getMinute();
        return bloques.stream()
                .filter(b -> aplicaEl(b, momento.toLocalDate()))
                .filter(b -> inicio(b) > minuto)
                .map(BloqueHorario::getHoraInicio)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private static int inicio(BloqueHorario b) {
        return b.getHoraInicio() == null ? 0 : b.getHoraInicio().toSecondOfDay() / 60;
    }

    private static int fin(BloqueHorario b) {
        int fin = b.getHoraFin() == null ? 24 * 60 : b.getHoraFin().toSecondOfDay() / 60;
        return fin <= inicio(b) ? 24 * 60 : fin;
    }
}
