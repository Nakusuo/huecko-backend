package com.huecko.backend.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** El secreto de los vectores de prueba del RFC 6238 (apéndice B): "12345678901234567890". */
    private static final String SECRETO_RFC = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    @DisplayName("coincide con los vectores del RFC 6238 (últimos 6 dígitos)")
    void vectoresRfc() {
        assertThat(SECRETO_RFC).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.codigo(SECRETO_RFC, Totp.paso(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        assertThat(Totp.codigo(SECRETO_RFC, Totp.paso(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
        assertThat(Totp.codigo(SECRETO_RFC, Totp.paso(Instant.ofEpochSecond(1234567890)))).isEqualTo("005924");
        assertThat(Totp.codigo(SECRETO_RFC, Totp.paso(Instant.ofEpochSecond(2000000000)))).isEqualTo("279037");
    }

    @Test
    @DisplayName("Base32 va y vuelve sin perder bytes")
    void base32IdaYVuelta() {
        String secreto = Totp.nuevoSecreto();
        assertThat(secreto).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Totp.base32(Totp.desdeBase32(secreto))).isEqualTo(secreto);
    }

    @Test
    @DisplayName("acepta el código del intervalo anterior y el siguiente, no más lejos")
    void margen() {
        Instant ahora = Instant.ofEpochSecond(1_700_000_000);
        long paso = Totp.paso(ahora);
        String secreto = Totp.nuevoSecreto();

        assertThat(Totp.verificar(secreto, Totp.codigo(secreto, paso - 1), ahora, null)).hasValue(paso - 1);
        assertThat(Totp.verificar(secreto, Totp.codigo(secreto, paso + 1), ahora, null)).hasValue(paso + 1);
        assertThat(Totp.verificar(secreto, Totp.codigo(secreto, paso - 3), ahora, null)).isEmpty();
    }

    @Test
    @DisplayName("un código ya usado no vale otra vez")
    void sinRepeticion() {
        Instant ahora = Instant.ofEpochSecond(1_700_000_000);
        long paso = Totp.paso(ahora);
        String secreto = Totp.nuevoSecreto();
        String codigo = Totp.codigo(secreto, paso);

        assertThat(Totp.verificar(secreto, codigo, ahora, paso)).isEmpty();
        assertThat(Totp.verificar(secreto, codigo, ahora, paso - 1)).hasValue(paso);
    }

    @Test
    @DisplayName("rechaza lo que no son 6 dígitos")
    void formato() {
        String secreto = Totp.nuevoSecreto();
        Instant ahora = Instant.now();
        assertThat(Totp.verificar(secreto, "12345", ahora, null)).isEmpty();
        assertThat(Totp.verificar(secreto, "abcdef", ahora, null)).isEmpty();
        assertThat(Totp.verificar(secreto, null, ahora, null)).isEmpty();
    }

    @Test
    @DisplayName("la dirección del QR lleva el emisor, la cuenta y el secreto")
    void uri() {
        assertThat(Totp.uri("Huecko", "ana@h.com", "ABC"))
                .isEqualTo("otpauth://totp/Huecko:ana%40h.com?secret=ABC&issuer=Huecko&algorithm=SHA1&digits=6&period=30");
    }
}
