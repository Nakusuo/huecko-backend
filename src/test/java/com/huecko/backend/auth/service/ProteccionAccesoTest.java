package com.huecko.backend.auth.service;

import com.huecko.backend.common.exception.DemasiadosIntentosException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProteccionAccesoTest {

    private final Instant t0 = Instant.parse("2026-10-10T12:00:00Z");
    private final ProteccionAcceso proteccion = new ProteccionAcceso();

    @Test
    @DisplayName("tras varios fallos con el mismo correo, ese correo queda frenado un rato")
    void frenaFallosPorCorreo() {
        for (int i = 0; i < ProteccionAcceso.MAX_FALLOS_POR_CORREO; i++) {
            proteccion.antesDeLogin("1.1.1." + i, "ana@h.com", t0);
            proteccion.loginFallido("ana@h.com", t0);
        }

        // Aunque venga de otra IP: así no se reparte el ataque entre máquinas.
        assertThatThrownBy(() -> proteccion.antesDeLogin("9.9.9.9", "ANA@h.com ", t0))
                .isInstanceOf(DemasiadosIntentosException.class);
        assertThatCode(() -> proteccion.antesDeLogin("9.9.9.9", "otra@h.com", t0)).doesNotThrowAnyException();
        assertThatCode(() -> proteccion.antesDeLogin("9.9.9.9", "ana@h.com",
                t0.plus(ProteccionAcceso.VENTANA_LOGIN).plusSeconds(1))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un login correcto borra los fallos acumulados de ese correo")
    void elExitoReinicia() {
        for (int i = 0; i < ProteccionAcceso.MAX_FALLOS_POR_CORREO - 1; i++) {
            proteccion.loginFallido("ana@h.com", t0);
        }
        proteccion.loginCorrecto("ana@h.com");
        proteccion.loginFallido("ana@h.com", t0);

        assertThatCode(() -> proteccion.antesDeLogin("1.1.1.1", "ana@h.com", t0)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una IP que prueba muchos correos distintos también se frena")
    void frenaPorIp() {
        for (int i = 0; i < ProteccionAcceso.MAX_LOGINS_POR_IP; i++) {
            proteccion.antesDeLogin("6.6.6.6", "u" + i + "@h.com", t0);
        }

        assertThatThrownBy(() -> proteccion.antesDeLogin("6.6.6.6", "nuevo@h.com", t0))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    @DisplayName("el registro tiene su propio límite por IP")
    void frenaRegistrosPorIp() {
        for (int i = 0; i < ProteccionAcceso.MAX_REGISTROS_POR_IP; i++) {
            proteccion.antesDeRegistro("7.7.7.7", t0);
        }

        assertThatThrownBy(() -> proteccion.antesDeRegistro("7.7.7.7", t0))
                .isInstanceOf(DemasiadosIntentosException.class);
        assertThatCode(() -> proteccion.antesDeRegistro("7.7.7.7",
                t0.plus(Duration.ofHours(1)).plusSeconds(1))).doesNotThrowAnyException();
    }
}
