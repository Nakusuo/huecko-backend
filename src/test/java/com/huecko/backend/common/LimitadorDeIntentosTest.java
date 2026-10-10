package com.huecko.backend.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LimitadorDeIntentosTest {

    private final Instant t0 = Instant.parse("2026-10-10T12:00:00Z");

    @Test
    @DisplayName("deja pasar hasta el máximo dentro de la ventana y frena el siguiente")
    void frenaAlSuperarElMaximo() {
        LimitadorDeIntentos limitador = new LimitadorDeIntentos(3, Duration.ofMinutes(10), 100);

        assertThat(limitador.permitir("ip", t0)).isTrue();
        assertThat(limitador.permitir("ip", t0)).isTrue();
        assertThat(limitador.permitir("ip", t0)).isTrue();
        assertThat(limitador.permitir("ip", t0)).isFalse();
    }

    @Test
    @DisplayName("pasada la ventana vuelve a dejar pasar")
    void seRecuperaConElTiempo() {
        LimitadorDeIntentos limitador = new LimitadorDeIntentos(1, Duration.ofMinutes(10), 100);
        limitador.permitir("ip", t0);

        assertThat(limitador.permitir("ip", t0.plus(Duration.ofMinutes(5)))).isFalse();
        assertThat(limitador.permitir("ip", t0.plus(Duration.ofMinutes(11)))).isTrue();
    }

    @Test
    @DisplayName("cada clave lleva su propia cuenta")
    void clavesIndependientes() {
        LimitadorDeIntentos limitador = new LimitadorDeIntentos(1, Duration.ofMinutes(10), 100);
        limitador.permitir("a", t0);

        assertThat(limitador.permitir("b", t0)).isTrue();
    }

    @Test
    @DisplayName("bloqueado() consulta sin gastar un intento; registrar() y olvidar() lo mueven a mano")
    void consultarRegistrarYOlvidar() {
        LimitadorDeIntentos limitador = new LimitadorDeIntentos(2, Duration.ofMinutes(10), 100);

        assertThat(limitador.bloqueado("ana", t0)).isFalse();
        limitador.registrar("ana", t0);
        limitador.registrar("ana", t0);
        assertThat(limitador.bloqueado("ana", t0)).isTrue();

        limitador.olvidar("ana");
        assertThat(limitador.bloqueado("ana", t0)).isFalse();
    }

    @Test
    @DisplayName("no guarda claves sin límite: al llenarse descarta las caducadas")
    void memoriaAcotada() {
        LimitadorDeIntentos limitador = new LimitadorDeIntentos(5, Duration.ofMinutes(10), 3);
        limitador.permitir("a", t0);
        limitador.permitir("b", t0);
        limitador.permitir("c", t0);

        limitador.permitir("d", t0.plus(Duration.ofMinutes(11)));

        assertThat(limitador.claves()).isLessThanOrEqualTo(3);
    }
}
