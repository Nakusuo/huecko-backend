package com.huecko.backend.observabilidad.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguracionVisibleTest {

    @Test
    @DisplayName("nunca muestra la clave JWT ni las credenciales de las bases")
    void sinSecretos() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("huecko.jwt.secret", "una-clave-muy-secreta-de-produccion-1234567890")
                .withProperty("spring.data.mongodb.uri", "mongodb://huecko:superclave@localhost:27017/huecko")
                .withProperty("spring.datasource.password", "otraclave");

        String todo = new ConfiguracionVisible(env).propiedades().toString();

        assertThat(todo).doesNotContain("una-clave-muy-secreta").doesNotContain("superclave").doesNotContain("otraclave");
        assertThat(todo).contains("mongodb://***@localhost:27017/huecko").contains("Propia (oculta)");
    }

    @Test
    @DisplayName("avisa si la clave JWT es la de desarrollo")
    void claveDeDesarrollo() {
        assertThat(ConfiguracionVisible.describirClave("cambia-esta-clave-de-desarrollo-x")).contains("desarrollo");
        assertThat(ConfiguracionVisible.describirClave(null)).isEqualTo("(sin definir)");
    }
}
