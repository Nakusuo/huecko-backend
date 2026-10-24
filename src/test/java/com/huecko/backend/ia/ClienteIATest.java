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
    void elEscaneoMandaLaFotoEnBase64ConSuTipoYLeeLosBloques() {
        servidor.expect(requestTo("http://ia/v1/horario/escaneo"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Huecko-Token", "secreto"))
                .andExpect(jsonPath("$.tipo").value("image/png"))
                .andExpect(jsonPath("$.imagen").value("AQID"))
                .andRespond(withSuccess("{\"bloques\":[{\"diaSemana\":1,\"horaInicio\":\"08:00\","
                        + "\"horaFin\":\"10:00\",\"titulo\":\"Cálculo\",\"dudoso\":false}]}",
                        MediaType.APPLICATION_JSON));

        var r = cliente.escanearHorario("image/png", new byte[]{1, 2, 3});

        assertThat(r.bloques()).containsExactly(
                new ClienteIA.BloqueLeido(1, "08:00", "10:00", "Cálculo", false));
        servidor.verify();
    }

    @Test
    void elBorradorMandaLosHuecosNumeradosYLeeLasOpciones() {
        servidor.expect(requestTo("http://ia/v1/planes/borrador"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.texto").value("cena el viernes"))
                .andExpect(jsonPath("$.huecos[0].numero").value(1))
                .andExpect(jsonPath("$.huecos[0].fecha").value("2026-10-23"))
                .andRespond(withSuccess("{\"titulo\":\"Cena\",\"categoria\":\"COMIDA\",\"lugar\":null,"
                        + "\"opciones\":[{\"numero\":1,\"horaInicio\":\"19:00\",\"horaFin\":\"21:00\"}],"
                        + "\"horasPlazo\":24}", MediaType.APPLICATION_JSON));

        var r = cliente.borradorPlan(new ClienteIA.PeticionBorrador("cena el viernes", "Amigos", 3,
                "jueves 22 de octubre de 2026, 10:00",
                java.util.List.of(new ClienteIA.HuecoIA(1, "2026-10-23", "viernes", "12:00", "22:00", 100, 3, 3))));

        assertThat(r.opciones()).containsExactly(new ClienteIA.OpcionIA(1, "19:00", "21:00"));
        assertThat(r.horasPlazo()).isEqualTo(24);
        servidor.verify();
    }

    @Test
    void lasIdeasMandanLosPlanesRecientesYLeenCadaIdea() {
        servidor.expect(requestTo("http://ia/v1/planes/ideas"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.planesRecientes[0]").value("Repaso"))
                .andRespond(withSuccess("{\"ideas\":[{\"titulo\":\"Almuerzo\",\"categoria\":\"COMIDA\","
                        + "\"lugar\":\"Cafetería\",\"numero\":1,\"razon\":\"todos libres al mediodía\"}]}",
                        MediaType.APPLICATION_JSON));

        var r = cliente.ideasPlan(new ClienteIA.PeticionIdeas("Amigos", "", 3, "jueves", java.util.List.of("Repaso"),
                java.util.List.of()));

        assertThat(r.ideas()).containsExactly(
                new ClienteIA.IdeaIA("Almuerzo", "COMIDA", "Cafetería", 1, "todos libres al mediodía"));
        servidor.verify();
    }

    @Test
    void elResumenMandaLasOpcionesAdmitidasYLeeLaRecomendacion() {
        servidor.expect(requestTo("http://ia/v1/planes/resumen"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.opciones[0]").value("CERRAR"))
                .andExpect(jsonPath("$.votacionExpres").doesNotExist())
                .andRespond(withSuccess("{\"resumen\":\"Va ganando el sábado.\",\"recomendacion\":\"CERRAR\","
                        + "\"razon\":\"el sábado tiene todos los votos\"}", MediaType.APPLICATION_JSON));

        var r = cliente.resumenVotacion(new ClienteIA.PeticionResumen("Repaso", null, "EN_VOTACION", true,
                "sábado 24 de octubre, 07:00", "jueves", 3, 2, java.util.List.of(), java.util.List.of(),
                java.util.List.of(), null, java.util.List.of("CERRAR", "ESPERAR")));

        assertThat(r).isEqualTo(new ClienteIA.RespuestaResumen("Va ganando el sábado.", "CERRAR",
                "el sábado tiene todos los votos"));
        servidor.verify();
    }

    @Test
    void unErrorDelServicioSaleComoIaNoDisponible() {
        servidor.expect(requestTo("http://ia/v1/criticidad")).andRespond(withServerError());

        assertThatThrownBy(() -> cliente.criticidad(PETICION)).isInstanceOf(IaNoDisponibleException.class);
    }
}
