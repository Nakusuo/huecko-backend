package com.huecko.backend.horario.service;

import com.huecko.backend.mongo.document.BloqueHorario;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Decide si dos bloques de horario ocupan el mismo rato.
 *
 * Es una clase pura, sin repositorios, para poder probar todas las
 * combinaciones de tipos sin simular Mongo. El service solo le pasa el bloque
 * candidato y los confirmados del usuario.
 *
 * Los bordes no cuentan: una clase de 8 a 10 y otra de 10 a 12 son un horario
 * normal de un estudiante, no un error.
 */
public final class DetectorSolapes {

    private static final Locale ES = Locale.forLanguageTag("es");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private DetectorSolapes() {
    }

    /** El primer bloque de `existentes` que choca con `candidato`, sin contar al propio candidato. */
    public static Optional<BloqueHorario> primerSolape(BloqueHorario candidato, List<BloqueHorario> existentes) {
        return existentes.stream()
                // Al editar, el bloque guardado sigue en la lista con sus horas
                // viejas: compararlo consigo mismo haría imposible moverlo media hora.
                .filter(otro -> candidato.getId() == null || !Objects.equals(candidato.getId(), otro.getId()))
                .filter(otro -> seSolapan(candidato, otro))
                .findFirst();
    }

    public static boolean seSolapan(BloqueHorario a, BloqueHorario b) {
        if (!horasSeCruzan(a.getHoraInicio(), a.getHoraFin(), b.getHoraInicio(), b.getHoraFin())) {
            return false;
        }
        boolean aRecurrente = a.getTipo() == BloqueHorario.Tipo.RECURRENTE;
        boolean bRecurrente = b.getTipo() == BloqueHorario.Tipo.RECURRENTE;

        if (aRecurrente && bRecurrente) {
            return Objects.equals(a.getDiaSemana(), b.getDiaSemana());
        }
        if (!aRecurrente && !bRecurrente) {
            return !a.getFecha().isAfter(ultimoDia(b)) && !b.getFecha().isAfter(ultimoDia(a));
        }
        BloqueHorario recurrente = aRecurrente ? a : b;
        BloqueHorario puntual = aRecurrente ? b : a;
        return puntualCaeEnDia(puntual, recurrente.getDiaSemana());
    }

    /** Solape estricto: tocarse en el borde no lo es. */
    public static boolean horasSeCruzan(LocalTime inicioA, LocalTime finA, LocalTime inicioB, LocalTime finB) {
        return inicioA.isBefore(finB) && inicioB.isBefore(finA);
    }

    /**
     * Texto para el usuario, p. ej. «Cálculo II» (lunes 08:00–11:00). Va en el
     * mensaje de error para que sepa qué bloque tiene que mover sin buscarlo.
     */
    public static String describir(BloqueHorario b) {
        String nombre = b.getEtiqueta() == null || b.getEtiqueta().isBlank()
                ? "otro bloque"
                : "«" + b.getEtiqueta().trim() + "»";
        String cuando;
        if (b.getTipo() == BloqueHorario.Tipo.RECURRENTE) {
            cuando = DayOfWeek.of(b.getDiaSemana()).getDisplayName(TextStyle.FULL, ES);
        } else if (b.getFechaFin() != null && !b.getFechaFin().equals(b.getFecha())) {
            cuando = "del " + FECHA.format(b.getFecha()) + " al " + FECHA.format(b.getFechaFin());
        } else {
            cuando = FECHA.format(b.getFecha());
        }
        return nombre + " (" + cuando + " " + HORA.format(b.getHoraInicio()) + "–" + HORA.format(b.getHoraFin()) + ")";
    }

    private static LocalDate ultimoDia(BloqueHorario puntual) {
        return puntual.getFechaFin() == null ? puntual.getFecha() : puntual.getFechaFin();
    }

    /**
     * Basta con mirar los siete primeros días del rango: a partir de ahí los
     * días de la semana se repiten, y un puntual de tres meses no obliga a
     * recorrer noventa fechas.
     */
    private static boolean puntualCaeEnDia(BloqueHorario puntual, Integer diaSemana) {
        if (diaSemana == null) {
            return false;
        }
        LocalDate fin = ultimoDia(puntual);
        LocalDate tope = puntual.getFecha().plusDays(6);
        if (fin.isAfter(tope)) {
            fin = tope;
        }
        for (LocalDate d = puntual.getFecha(); !d.isAfter(fin); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() == diaSemana) {
                return true;
            }
        }
        return false;
    }
}
