package com.huecko.backend.ia.service;

import com.huecko.backend.ia.dto.UsoIAResponse;
import com.huecko.backend.mongo.document.LlamadaIA;
import com.huecko.backend.mongo.repository.LlamadaIARepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstadisticasUsoIATest {

    private static final Instant AHORA = Instant.parse("2026-10-23T15:00:00Z");

    @Mock
    private LlamadaIARepository llamadas;

    private static LlamadaIA llamada(LlamadaIA.Funcion funcion, boolean ok, long ms) {
        return LlamadaIA.builder().funcion(funcion).ok(ok).duracionMs(ms).ocurridaEn(AHORA).build();
    }

    @Test
    @DisplayName("Cuenta por función el último mes, con éxito y tiempo medio; las no usadas salen a cero")
    void porFuncion() {
        when(llamadas.findByOcurridaEnAfter(AHORA.minus(RegistroUsoIA.RETENCION))).thenReturn(List.of(
                llamada(LlamadaIA.Funcion.ESCANEO_HORARIO, true, 2000),
                llamada(LlamadaIA.Funcion.ESCANEO_HORARIO, true, 3000),
                llamada(LlamadaIA.Funcion.ESCANEO_HORARIO, false, 7000),
                llamada(LlamadaIA.Funcion.RESUMEN_VOTACION, true, 900)));

        UsoIAResponse uso = new EstadisticasUsoIA(llamadas, Clock.fixed(AHORA, ZoneOffset.UTC)).calcular();

        assertThat(uso.dias()).isEqualTo(30);
        assertThat(uso.llamadas()).isEqualTo(4);
        assertThat(uso.funciones()).containsExactly(
                new UsoIAResponse.PorFuncion("ESCANEO_HORARIO", 3, 2, 2.0 / 3, 4000L),
                new UsoIAResponse.PorFuncion("BORRADOR_PLAN", 0, 0, null, null),
                new UsoIAResponse.PorFuncion("IDEAS_PLAN", 0, 0, null, null),
                new UsoIAResponse.PorFuncion("RESUMEN_VOTACION", 1, 1, 1.0, 900L));
    }
}
