package com.huecko.backend.ia.service;

import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.grupo.dto.DisponibilidadResponse;
import com.huecko.backend.grupo.dto.VentanaSugeridaResponse;
import com.huecko.backend.grupo.service.GrupoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HuecosDelGrupoTest {

    private static final UUID USUARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GRUPO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    /** Jueves. */
    private static final LocalDate HOY = LocalDate.of(2026, 10, 22);
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 19);

    @Mock
    private GrupoService grupoService;

    private static Instant a(LocalDate fecha, int hora, int minuto) {
        return LocalDateTime.of(fecha, LocalTime.of(hora, minuto)).atZone(ZonaHoraria.ZONA).toInstant();
    }

    private void semana(LocalDate lunes, VentanaSugeridaResponse... ventanas) {
        lenient().when(grupoService.disponibilidad(eq(USUARIO), eq(GRUPO), isNull(), eq(lunes)))
                .thenReturn(new DisponibilidadResponse(GRUPO, 80, 3, lunes, lunes.plusDays(6), 8, 20,
                        List.of(), List.of(ventanas)));
    }

    private static VentanaSugeridaResponse v(int dia, int desde, int hasta) {
        return new VentanaSugeridaResponse("d" + dia + "-" + desde, dia, LocalTime.of(desde, 0),
                LocalTime.of(hasta, 0), 100, 3, 3);
    }

    @Test
    @DisplayName("Da fecha a cada hueco, los ordena y los numera desde 1")
    void fechasYNumeros() {
        semana(LUNES, v(5, 8, 12), v(4, 14, 18));
        semana(LUNES.plusWeeks(1), v(1, 8, 20));
        semana(LUNES.plusWeeks(2));

        var huecos = new HuecosDelGrupo(grupoService).calcular(USUARIO, GRUPO, a(HOY, 9, 0));

        assertThat(huecos).extracting(HuecosDelGrupo.Hueco::numero, HuecosDelGrupo.Hueco::fecha, HuecosDelGrupo.Hueco::inicio)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, HOY, LocalTime.of(14, 0)),
                        org.assertj.core.groups.Tuple.tuple(2, HOY.plusDays(1), LocalTime.of(8, 0)),
                        org.assertj.core.groups.Tuple.tuple(3, LocalDate.of(2026, 10, 26), LocalTime.of(8, 0)));
    }

    @Test
    @DisplayName("Lo que ya pasó o empieza en menos de 2 horas no se ofrece; el resto del día de hoy, sí")
    void margenDeHoy() {
        semana(LUNES, v(3, 8, 20), v(4, 8, 20));
        semana(LUNES.plusWeeks(1));
        semana(LUNES.plusWeeks(2));

        var huecos = new HuecosDelGrupo(grupoService).calcular(USUARIO, GRUPO, a(HOY, 10, 30));

        // Miércoles ya pasó; del jueves queda desde las 13:00 (10:30 + 2 h, en punto).
        assertThat(huecos).singleElement().satisfies(h -> {
            assertThat(h.fecha()).isEqualTo(HOY);
            assertThat(h.inicio()).isEqualTo(LocalTime.of(13, 0));
            assertThat(h.fin()).isEqualTo(LocalTime.of(20, 0));
        });
    }

    @Test
    @DisplayName("Si hoy ya no queda margen, el hueco de hoy desaparece")
    void hoyYaNoCabe() {
        semana(LUNES, v(4, 8, 20));
        semana(LUNES.plusWeeks(1));
        semana(LUNES.plusWeeks(2));

        assertThat(new HuecosDelGrupo(grupoService).calcular(USUARIO, GRUPO, a(HOY, 19, 0))).isEmpty();
    }

    @Test
    @DisplayName("Mira solo 14 días, aunque eso abarque tres semanas")
    void soloDosSemanas() {
        semana(LUNES);
        semana(LUNES.plusWeeks(1));
        // 4 de noviembre es el día 14 contando hoy; el 5, ya no.
        semana(LUNES.plusWeeks(2), v(3, 8, 10), v(4, 8, 10));

        var huecos = new HuecosDelGrupo(grupoService).calcular(USUARIO, GRUPO, a(HOY, 9, 0));

        assertThat(huecos).extracting(HuecosDelGrupo.Hueco::fecha).containsExactly(LocalDate.of(2026, 11, 4));
    }

    @Test
    @DisplayName("Como mucho 30 huecos")
    void tope() {
        List<VentanaSugeridaResponse> muchas = new ArrayList<>();
        for (int dia = 1; dia <= 7; dia++) {
            for (int hora = 8; hora < 20; hora += 2) {
                muchas.add(v(dia, hora, hora + 1));
            }
        }
        when(grupoService.disponibilidad(eq(USUARIO), eq(GRUPO), isNull(), any()))
                .thenAnswer(inv -> {
                    LocalDate lunes = inv.getArgument(3);
                    return new DisponibilidadResponse(GRUPO, 80, 3, lunes, lunes.plusDays(6), 8, 20, List.of(), muchas);
                });

        var huecos = new HuecosDelGrupo(grupoService).calcular(USUARIO, GRUPO, a(HOY, 9, 0));

        assertThat(huecos).hasSize(HuecosDelGrupo.MAX_HUECOS);
        assertThat(huecos.get(huecos.size() - 1).numero()).isEqualTo(HuecosDelGrupo.MAX_HUECOS);
    }
}
