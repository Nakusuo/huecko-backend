package com.huecko.backend.ia;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClienteIATest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final ClienteIA cliente = new ClienteIA(builder, "http://ia", "secreto");

    private static final ClienteIA.PeticionCriticidad PETICION =
            new ClienteIA.PeticionCriticidad("Cena", null, "MIEMBRO", "Llevo el coche");

    @Test
    void mandaElTokenYLeeElVeredicto() {
        servidor.expect(requestTo("http://ia/v1/criticidad"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Huecko-Token", "secreto"))
                .andExpect(jsonPath("$.motivo").value("Llevo el coche"))
                .andRespond(withSuccess("{\"criticidad\":\"CRITICA\",\"razon\":\"lleva el coche del grupo\"}",
                        MediaType.APPLICATION_JSON));

        var r = cliente.criticidad(PETICION);

        assertThat(r).isEqualTo(new ClienteIA.RespuestaCriticidad("CRITICA", "lleva el coche del grupo"));
        servidor.verify();
    }

    @Test
    void unErrorDelServicioSaleComoIaNoDisponible() {
        servidor.expect(requestTo("http://ia/v1/criticidad")).andRespond(withServerError());

        assertThatThrownBy(() -> cliente.criticidad(PETICION)).isInstanceOf(IaNoDisponibleException.class);
    }
}
