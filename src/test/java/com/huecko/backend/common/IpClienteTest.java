package com.huecko.backend.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class IpClienteTest {

    @Test
    @DisplayName("prefiere CF-Connecting-IP, que el cliente no puede falsear")
    void cloudflarePrimero() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("CF-Connecting-IP", "203.0.113.7");
        req.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.1");

        assertThat(IpCliente.de(req)).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("sin Cloudflare usa la primera IP de X-Forwarded-For")
    void primeraReenviada() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", " 198.51.100.2 , 10.0.0.1");

        assertThat(IpCliente.de(req)).isEqualTo("198.51.100.2");
    }

    @Test
    @DisplayName("sin proxy, la dirección de la conexión")
    void sinProxy() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");

        assertThat(IpCliente.de(req)).isEqualTo("127.0.0.1");
    }
}
