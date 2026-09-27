package com.huecko.backend.horario.service;

import com.huecko.backend.mongo.document.BloqueHorario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La regla de solape sin repositorios: todas las combinaciones de tipos, y
 * los bordes, que es donde suele colarse el error de un `<=` de más.
 */
class DetectorSolapesTest {

    /** Lunes 7 de septiembre de 2026. */
    private static final LocalDate LUNES = LocalDate.of(2026, 9, 7);

    @Test
    @DisplayName("Recurrentes del mismo día con horas cruzadas se solapan")
    void recurrentesMismoDia() {
        assertThat(DetectorSolapes.seSolapan(
                recurrente(1, "08:00", "11:00"), recurrente(1, "10:00", "12:00"))).isTrue();
    }

    @Test
    @DisplayName("Recurrentes de días distintos no se solapan aunque compartan horas")
    void recurrentesDiasDistintos() {
        assertThat(DetectorSolapes.seSolapan(
                recurrente(1, "08:00", "11:00"), recurrente(2, "08:00", "11:00"))).isFalse();
    }

    @Test
    @DisplayName("Tocarse en el borde no es solape")
    void bordeNoEsSolape() {
        assertThat(DetectorSolapes.seSolapan(
                recurrente(1, "08:00", "10:00"), recurrente(1, "10:00", "12:00"))).isFalse();
        assertThat(DetectorSolapes.seSolapan(
                recurrente(1, "10:00", "12:00"), recurrente(1, "08:00", "10:00"))).isFalse();
    }

    @Test
    @DisplayName("Un bloque que contiene a otro entero se solapa")
    void contenidoSeSolapa() {
        assertThat(DetectorSolapes.seSolapan(
                recurrente(3, "08:00", "18:00"), recurrente(3, "12:00", "13:00"))).isTrue();
    }

    @Test
    @DisplayName("Puntuales: se solapan solo si los rangos de fechas se cruzan")
    void puntuales() {
        BloqueHorario viaje = puntual(LUNES, LUNES.plusDays(2), "09:00", "13:00");
        assertThat(DetectorSolapes.seSolapan(viaje, puntual(LUNES.plusDays(2), null, "12:00", "14:00"))).isTrue();
        assertThat(DetectorSolapes.seSolapan(viaje, puntual(LUNES.plusDays(3), null, "12:00", "14:00"))).isFalse();
        // Sin fechaFin el bloque dura solo su día.
        assertThat(DetectorSolapes.seSolapan(
                puntual(LUNES, null, "09:00", "13:00"), puntual(LUNES.plusDays(1), null, "09:00", "13:00")))
                .isFalse();
    }

    @Test
    @DisplayName("Recurrente contra puntual: choca si algún día del rango cae en su día de la semana")
    void recurrenteContraPuntual() {
        BloqueHorario miercoles = recurrente(3, "10:00", "12:00");
        // Lunes a miércoles: incluye un miércoles.
        assertThat(DetectorSolapes.seSolapan(miercoles, puntual(LUNES, LUNES.plusDays(2), "11:00", "13:00")))
                .isTrue();
        // Lunes a martes: no hay miércoles.
        assertThat(DetectorSolapes.seSolapan(puntual(LUNES, LUNES.plusDays(1), "11:00", "13:00"), miercoles))
                .isFalse();
    }

    @Test
    @DisplayName("Un puntual largo se resuelve mirando solo una semana")
    void puntualLargo() {
        BloqueHorario domingo = recurrente(7, "10:00", "12:00");
        assertThat(DetectorSolapes.seSolapan(domingo, puntual(LUNES, LUNES.plusDays(90), "09:00", "11:00")))
                .isTrue();
    }

    @Test
    @DisplayName("primerSolape ignora al propio bloque por id")
    void primerSolapeIgnoraAlPropio() {
        BloqueHorario propio = recurrente(1, "08:00", "10:00");
        propio.setId("b1");
        BloqueHorario editado = recurrente(1, "08:30", "10:30");
        editado.setId("b1");

        assertThat(DetectorSolapes.primerSolape(editado, List.of(propio))).isEmpty();
    }

    @Test
    @DisplayName("El mensaje nombra el bloque, el día y las horas")
    void describir() {
        BloqueHorario calculo = recurrente(1, "08:00", "11:00");
        calculo.setEtiqueta("Cálculo II");
        assertThat(DetectorSolapes.describir(calculo)).isEqualTo("«Cálculo II» (lunes 08:00–11:00)");

        assertThat(DetectorSolapes.describir(puntual(LUNES, LUNES.plusDays(2), "09:00", "13:00")))
                .isEqualTo("otro bloque (del 07/09/2026 al 09/09/2026 09:00–13:00)");
    }

    private BloqueHorario recurrente(int dia, String inicio, String fin) {
        return BloqueHorario.builder()
                .tipo(BloqueHorario.Tipo.RECURRENTE)
                .diaSemana(dia)
                .horaInicio(LocalTime.parse(inicio))
                .horaFin(LocalTime.parse(fin))
                .build();
    }

    private BloqueHorario puntual(LocalDate fecha, LocalDate fechaFin, String inicio, String fin) {
        return BloqueHorario.builder()
                .tipo(BloqueHorario.Tipo.PUNTUAL)
                .fecha(fecha)
                .fechaFin(fechaFin)
                .horaInicio(LocalTime.parse(inicio))
                .horaFin(LocalTime.parse(fin))
                .build();
    }
}
