package com.huecko.backend.grupo.service;

import com.huecko.backend.grupo.dto.CeldaDisponibilidadResponse;
import com.huecko.backend.grupo.dto.DisponibilidadResponse;
import com.huecko.backend.grupo.dto.VentanaSugeridaResponse;
import com.huecko.backend.mongo.document.BloqueHorario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El algoritmo de cruce (RF-05, RF-06) no toca ninguna base de datos, así que
 * se prueba directo. Todas las fechas usan la semana del lunes 7 de septiembre
 * de 2026 para que los bloques puntuales sean deterministas.
 */
class CalculadoraDisponibilidadTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 9, 7);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 9, 9);

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();
    private static final UUID CARLA = UUID.randomUUID();
    private static final UUID DIEGO = UUID.randomUUID();

    private final CalculadoraDisponibilidad calculadora = new CalculadoraDisponibilidad();

    @Test
    @DisplayName("Sin ningún bloque, el grupo entero está libre en todas las casillas")
    void grupoSinBloquesEstaLibre() {
        DisponibilidadResponse r = calcular(List.of(ANA, BRUNO), List.of(), 100);

        assertThat(r.totalMiembros()).isEqualTo(2);
        // 7 días x 12 horas (08:00-20:00)
        assertThat(r.celdas()).hasSize(84);
        assertThat(r.celdas()).allSatisfy(c -> {
            assertThat(c.disponibles()).isEqualTo(2);
            assertThat(c.porcentaje()).isEqualTo(100);
            assertThat(c.cumpleUmbral()).isTrue();
        });
    }

    @Test
    @DisplayName("RF-05: un bloque resta a esa persona solo en las horas que ocupa")
    void unBloqueOcupaSusHorasYNoMas() {
        // Ana ocupada el lunes de 08:00 a 11:00.
        DisponibilidadResponse r = calcular(
                List.of(ANA, BRUNO),
                List.of(recurrente(ANA, 1, "08:00", "11:00")),
                100);

        assertThat(disponiblesEn(r, 1, 8)).isEqualTo(1);
        assertThat(disponiblesEn(r, 1, 10)).isEqualTo(1);
        // A las 11 ya está libre: el bloque cierra a las 11:00 en punto.
        assertThat(disponiblesEn(r, 1, 11)).isEqualTo(2);
        // Y el martes no la afecta.
        assertThat(disponiblesEn(r, 2, 8)).isEqualTo(2);
    }

    @Test
    @DisplayName("Un bloque que acaba a y media también ocupa esa hora: no se puede empezar un plan ahí")
    void bloqueQueTerminaAMediaOcupaLaFranja() {
        DisponibilidadResponse r = calcular(
                List.of(ANA, BRUNO),
                List.of(recurrente(ANA, 1, "08:00", "10:30")),
                100);

        assertThat(disponiblesEn(r, 1, 9)).isEqualTo(1);
        assertThat(disponiblesEn(r, 1, 10)).isEqualTo(1);
        assertThat(disponiblesEn(r, 1, 11)).isEqualTo(2);
    }

    @Test
    @DisplayName("Dos bloques solapados de la MISMA persona cuentan como una sola ausencia")
    void bloquesSolapadosDeLaMismaPersonaNoRestanDosVeces() {
        DisponibilidadResponse r = calcular(
                List.of(ANA, BRUNO),
                List.of(recurrente(ANA, 1, "08:00", "12:00"),
                        recurrente(ANA, 1, "09:00", "10:00")),
                100);

        // Si se contara por bloque y no por persona, saldría 0 disponibles.
        assertThat(disponiblesEn(r, 1, 9)).isEqualTo(1);
    }

    @Test
    @DisplayName("RF-06: bajar el umbral convierte en válidas franjas que la unanimidad descartaba")
    void elUmbralDecideQueCasillasCuentan() {
        // 1 de 4 ocupado -> 75 % libre.
        List<BloqueHorario> bloques = List.of(recurrente(ANA, 1, "08:00", "12:00"));
        List<UUID> grupo = List.of(ANA, BRUNO, CARLA, DIEGO);

        assertThat(celda(calcular(grupo, bloques, 100), 1, 9).cumpleUmbral()).isFalse();
        assertThat(celda(calcular(grupo, bloques, 80), 1, 9).cumpleUmbral()).isFalse();
        assertThat(celda(calcular(grupo, bloques, 70), 1, 9).cumpleUmbral()).isTrue();
        assertThat(celda(calcular(grupo, bloques, 70), 1, 9).porcentaje()).isEqualTo(75);
    }

    @Test
    @DisplayName("Las horas consecutivas con quórum se agrupan en una sola ventana")
    void lasHorasConsecutivasSeAgrupan() {
        // Todo el mundo ocupado salvo de 10:00 a 13:00 el martes.
        List<BloqueHorario> bloques = List.of(
                recurrente(ANA, 2, "08:00", "10:00"),
                recurrente(ANA, 2, "13:00", "20:00"),
                recurrente(BRUNO, 2, "08:00", "10:00"),
                recurrente(BRUNO, 2, "13:00", "20:00"));

        DisponibilidadResponse r = calcular(List.of(ANA, BRUNO), bloques, 100);

        VentanaSugeridaResponse martes = r.ventanasSugeridas().stream()
                .filter(v -> v.diaSemana() == 2)
                .findFirst()
                .orElseThrow();

        assertThat(martes.horaInicio()).isEqualTo(LocalTime.of(10, 0));
        assertThat(martes.horaFin()).isEqualTo(LocalTime.of(13, 0));
        assertThat(martes.disponibilidadPorcentaje()).isEqualTo(100);
    }

    @Test
    @DisplayName("La ventana se anuncia con el porcentaje de su peor hora, no con el promedio")
    void laVentanaUsaElMinimoNoElPromedio() {
        // Miércoles: 10-11 libre todo el grupo, 11-12 con uno de cuatro ocupado.
        List<BloqueHorario> bloques = new ArrayList<>();
        for (UUID quien : List.of(ANA, BRUNO, CARLA, DIEGO)) {
            bloques.add(recurrente(quien, 3, "08:00", "10:00"));
            bloques.add(recurrente(quien, 3, "12:00", "20:00"));
        }
        bloques.add(recurrente(DIEGO, 3, "11:00", "12:00"));

        DisponibilidadResponse r = calcular(List.of(ANA, BRUNO, CARLA, DIEGO), bloques, 70);

        VentanaSugeridaResponse miercoles = r.ventanasSugeridas().stream()
                .filter(v -> v.diaSemana() == 3)
                .findFirst()
                .orElseThrow();

        assertThat(miercoles.horaInicio()).isEqualTo(LocalTime.of(10, 0));
        assertThat(miercoles.horaFin()).isEqualTo(LocalTime.of(12, 0));
        // El promedio sería 87; lo que se puede garantizar las dos horas es 75.
        assertThat(miercoles.disponibilidadPorcentaje()).isEqualTo(75);
        assertThat(miercoles.miembrosDisponibles()).isEqualTo(3);
    }

    @Test
    @DisplayName("Un bloque puntual de esta semana ocupa; el de otra semana no")
    void elBloquePuntualSoloAfectaASuSemana() {
        BloqueHorario estaSemana = puntual(ANA, MIERCOLES, null, "09:00", "11:00");
        BloqueHorario elMesQueViene = puntual(ANA, MIERCOLES.plusWeeks(4), null, "09:00", "11:00");

        DisponibilidadResponse r = calcular(
                List.of(ANA, BRUNO), List.of(estaSemana, elMesQueViene), 100);

        // Miércoles = día 3.
        assertThat(disponiblesEn(r, 3, 9)).isEqualTo(1);
        // Si el de dentro de un mes contara, restaría dos veces la misma casilla.
        assertThat(disponiblesEn(r, 3, 11)).isEqualTo(2);
    }

    @Test
    @DisplayName("Un bloque puntual con fechaFin ocupa todos los días del rango")
    void elRangoDeUnPuntualOcupaTodosSusDias() {
        // Viaje de lunes a miércoles.
        BloqueHorario viaje = puntual(ANA, LUNES, LUNES.plusDays(2), "08:00", "20:00");

        DisponibilidadResponse r = calcular(List.of(ANA, BRUNO), List.of(viaje), 100);

        assertThat(disponiblesEn(r, 1, 9)).isEqualTo(1);
        assertThat(disponiblesEn(r, 2, 9)).isEqualTo(1);
        assertThat(disponiblesEn(r, 3, 9)).isEqualTo(1);
        assertThat(disponiblesEn(r, 4, 9)).isEqualTo(2);
    }

    @Test
    @DisplayName("Un rango puntual que empieza antes del lunes se recorta a la semana mostrada")
    void elRangoQueCruzaLaSemanaSeRecorta() {
        BloqueHorario viaje = puntual(ANA, LUNES.minusDays(3), LUNES.plusDays(1), "08:00", "20:00");

        DisponibilidadResponse r = calcular(List.of(ANA, BRUNO), List.of(viaje), 100);

        assertThat(disponiblesEn(r, 1, 9)).isEqualTo(1);
        assertThat(disponiblesEn(r, 2, 9)).isEqualTo(1);
        assertThat(disponiblesEn(r, 3, 9)).isEqualTo(2);
    }

    @Test
    @DisplayName("Los bloques de quien ya no pertenece al grupo se ignoran")
    void losBloquesDeUnNoMiembroNoBloquean() {
        // Carla ya no está en el grupo, pero sus bloques siguen en la base.
        DisponibilidadResponse r = calcular(
                List.of(ANA, BRUNO),
                List.of(recurrente(CARLA, 1, "08:00", "20:00")),
                100);

        assertThat(r.totalMiembros()).isEqualTo(2);
        assertThat(disponiblesEn(r, 1, 9)).isEqualTo(2);
    }

    @Test
    @DisplayName("Un grupo sin integrantes devuelve una respuesta vacía en vez de dividir por cero")
    void grupoVacioNoRompe() {
        DisponibilidadResponse r = calcular(List.of(), List.of(), 100);

        assertThat(r.totalMiembros()).isZero();
        assertThat(r.celdas()).isEmpty();
        assertThat(r.ventanasSugeridas()).isEmpty();
    }

    @Test
    @DisplayName("Una ventana pegada al final de la franja se cierra igual")
    void laVentanaFinalSeCierra() {
        // Ocupados todo el día salvo la última hora, 19:00-20:00.
        DisponibilidadResponse r = calcular(
                List.of(ANA),
                List.of(recurrente(ANA, 5, "08:00", "19:00")),
                100);

        VentanaSugeridaResponse viernes = r.ventanasSugeridas().stream()
                .filter(v -> v.diaSemana() == 5)
                .findFirst()
                .orElseThrow();

        assertThat(viernes.horaInicio()).isEqualTo(LocalTime.of(19, 0));
        assertThat(viernes.horaFin()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    @DisplayName("La semana se normaliza al lunes, se pida el día que se pida")
    void laSemanaSeNormalizaAlLunes() {
        DisponibilidadResponse desdeElJueves = calculadora.calcular(
                UUID.randomUUID(), List.of(ANA), List.of(), 100, LUNES.plusDays(3));

        assertThat(desdeElJueves.semanaDesde()).isEqualTo(LUNES);
        assertThat(desdeElJueves.semanaHasta()).isEqualTo(LUNES.plusDays(6));
    }

    @Test
    @DisplayName("RNF-04: 20 personas con horario cargado se cruzan en mucho menos de 1 segundo")
    void veintePersonasSeCruzanRapido() {
        List<UUID> grupo = new ArrayList<>();
        List<BloqueHorario> bloques = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            UUID persona = UUID.randomUUID();
            grupo.add(persona);
            for (int dia = 1; dia <= 5; dia++) {
                bloques.add(recurrente(persona, dia, "08:00", "12:00"));
                bloques.add(recurrente(persona, dia, "14:00", "18:00"));
            }
        }

        long inicio = System.nanoTime();
        DisponibilidadResponse r = calcular(grupo, bloques, 80);
        long milis = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(r.totalMiembros()).isEqualTo(20);
        assertThat(milis).isLessThan(1000L);
    }

    /* ------------------------------------------------------------------ */

    private DisponibilidadResponse calcular(List<UUID> miembros, List<BloqueHorario> bloques, int umbral) {
        return calculadora.calcular(UUID.randomUUID(), miembros, bloques, umbral, LUNES);
    }

    private int disponiblesEn(DisponibilidadResponse r, int dia, int hora) {
        return celda(r, dia, hora).disponibles();
    }

    private CeldaDisponibilidadResponse celda(DisponibilidadResponse r, int dia, int hora) {
        return r.celdas().stream()
                .filter(c -> c.diaSemana() == dia && c.hora() == hora)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No existe la casilla dia=" + dia + " hora=" + hora));
    }

    private BloqueHorario recurrente(UUID usuario, int diaSemana, String inicio, String fin) {
        return BloqueHorario.builder()
                .usuarioId(usuario.toString())
                .tipo(BloqueHorario.Tipo.RECURRENTE)
                .diaSemana(diaSemana)
                .horaInicio(LocalTime.parse(inicio))
                .horaFin(LocalTime.parse(fin))
                .estado(BloqueHorario.Estado.CONFIRMADO)
                .build();
    }

    private BloqueHorario puntual(UUID usuario, LocalDate fecha, LocalDate fechaFin, String inicio, String fin) {
        return BloqueHorario.builder()
                .usuarioId(usuario.toString())
                .tipo(BloqueHorario.Tipo.PUNTUAL)
                .fecha(fecha)
                .fechaFin(fechaFin)
                .horaInicio(LocalTime.parse(inicio))
                .horaFin(LocalTime.parse(fin))
                .estado(BloqueHorario.Estado.CONFIRMADO)
                .build();
    }
}
