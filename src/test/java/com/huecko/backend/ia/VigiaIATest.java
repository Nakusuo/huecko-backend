package com.huecko.backend.ia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * En el plan gratuito de Render la IA se duerme y la API no la espera (3 s):
 * sin despertarla, casi todo salía por reglas. VigiaIA la despierta y deja
 * su estado a mano para el panel de salud.
 */
class VigiaIATest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();

    @Test
    @DisplayName("si la IA responde con Gemini, queda ACTIVA")
    void activa() {
        servidor.expect(requestTo("http://ia/salud"))
                .andRespond(withSuccess("{\"estado\":\"ok\",\"gemini\":true}", MediaType.APPLICATION_JSON));
        VigiaIA vigia = new VigiaIA(builder, "http://ia", true);

        vigia.comprobar();

        assertThat(vigia.ultimo().estado()).isEqualTo(VigiaIA.Estado.ACTIVA);
        assertThat(vigia.ultimo().comprobadoEn()).isNotNull();
    }

    @Test
    @DisplayName("si responde pero sin clave de Gemini, queda SIN_GEMINI")
    void sinGemini() {
        servidor.expect(requestTo("http://ia/salud"))
                .andRespond(withSuccess("{\"estado\":\"ok\",\"gemini\":false}", MediaType.APPLICATION_JSON));
        VigiaIA vigia = new VigiaIA(builder, "http://ia", true);

        vigia.comprobar();

        assertThat(vigia.ultimo().estado()).isEqualTo(VigiaIA.Estado.SIN_GEMINI);
    }

    @Test
    @DisplayName("si no responde, queda SIN_RESPUESTA y no lanza")
    void sinRespuesta() {
        servidor.expect(requestTo("http://ia/salud")).andRespond(withServerError());
        VigiaIA vigia = new VigiaIA(builder, "http://ia", true);

        vigia.comprobar();

        assertThat(vigia.ultimo().estado()).isEqualTo(VigiaIA.Estado.SIN_RESPUESTA);
    }

    @Test
    @DisplayName("con la IA desactivada no llama a nadie")
    void desactivada() {
        servidor.expect(never(), requestTo("http://ia/salud"));
        VigiaIA vigia = new VigiaIA(builder, "http://ia", false);

        vigia.comprobar();

        assertThat(vigia.ultimo().estado()).isEqualTo(VigiaIA.Estado.DESACTIVADA);
        servidor.verify();
    }
}
