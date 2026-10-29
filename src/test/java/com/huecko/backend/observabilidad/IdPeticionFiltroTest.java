package com.huecko.backend.observabilidad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class IdPeticionFiltroTest {

    private final IdPeticionFiltro filtro = new IdPeticionFiltro();

    /** Atiende la petición y devuelve el id que vio el código durante ella. */
    private String atender(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicReference<String> visto = new AtomicReference<>();
        filtro.doFilter(request, response, (req, res) -> visto.set(IdPeticionFiltro.actual()));
        return visto.get();
    }

    @Test
    @DisplayName("genera un id, lo pone en la respuesta y lo limpia al terminar")
    void generaYLimpia() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String id = atender(new MockHttpServletRequest("GET", "/api/grupos"), response);

        assertThat(id).matches("[a-z2-9]{12}");
        assertThat(response.getHeader(IdPeticionFiltro.CABECERA)).isEqualTo(id);
        assertThat(IdPeticionFiltro.actual()).isNull();
    }

    @Test
    @DisplayName("respeta el id que trae la petición si es válido")
    void respetaElRecibido() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/grupos");
        request.addHeader(IdPeticionFiltro.CABECERA, "front-12345678");

        assertThat(atender(request, new MockHttpServletResponse())).isEqualTo("front-12345678");
    }

    @Test
    @DisplayName("no acepta ids raros, que podrían ensuciar el log")
    void rechazaInvalidos() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/grupos");
        request.addHeader(IdPeticionFiltro.CABECERA, "a b\nINFO falso");

        assertThat(atender(request, new MockHttpServletResponse())).matches("[a-z2-9]{12}");
    }
}
