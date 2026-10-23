package com.huecko.backend.ia.service;

import com.huecko.backend.common.exception.DemasiadosIntentosException;
import com.huecko.backend.common.exception.ServicioNoDisponibleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccesoAsistenteTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LUIS = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final RelojMovible reloj = new RelojMovible(Instant.parse("2026-10-23T15:00:00Z"));

    @Test
    @DisplayName("Apagada, responde 503 sin gastar nada")
    void apagadaNoAtiende() {
        AccesoAsistente acceso = new AccesoAsistente(false, reloj);

        assertThatThrownBy(() -> acceso.exigir(ANA)).isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    @DisplayName("El cupo es por persona: agotar el mío no frena a otra")
    void cupoPorPersona() {
        AccesoAsistente acceso = new AccesoAsistente(true, reloj);
        gastarTodo(acceso, ANA);

        assertThatThrownBy(() -> acceso.exigir(ANA))
                .isInstanceOf(DemasiadosIntentosException.class)
                .hasMessageContaining("Espera unos minutos");
        assertThatCode(() -> acceso.exigir(LUIS)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Pasada la ventana, el cupo vuelve")
    void elCupoVuelve() {
        AccesoAsistente acceso = new AccesoAsistente(true, reloj);
        gastarTodo(acceso, ANA);

        reloj.avanzar(AccesoAsistente.VENTANA.plusSeconds(1));

        assertThatCode(() -> acceso.exigir(ANA)).doesNotThrowAnyException();
    }

    private static void gastarTodo(AccesoAsistente acceso, UUID usuario) {
        for (int i = 0; i < AccesoAsistente.MAX_USOS; i++) {
            acceso.exigir(usuario);
        }
    }

    private static final class RelojMovible extends Clock {
        private Instant ahora;

        RelojMovible(Instant ahora) {
            this.ahora = ahora;
        }

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }
    }
}
