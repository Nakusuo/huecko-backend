package com.huecko.backend.observabilidad.service;

import com.huecko.backend.mongo.document.FalloRegistrado.Origen;
import com.huecko.backend.mongo.repository.FalloRegistradoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegistroFallosTest {

    @Test
    @DisplayName("la ruta agrupa por endpoint: los ids se sustituyen y la query se quita")
    void normalizaRuta() {
        assertThat(RegistroFallos.normalizarRuta("/api/planes/3f2b8c1e-1234-4abc-9def-0123456789ab/cerrar?x=1"))
                .isEqualTo("/api/planes/{id}/cerrar");
        assertThat(RegistroFallos.normalizarRuta("/groups/42")).isEqualTo("/groups/{id}");
        assertThat(RegistroFallos.normalizarRuta("/api/votaciones/65f1a2b3c4d5e6f708192a3b"))
                .isEqualTo("/api/votaciones/{id}");
        assertThat(RegistroFallos.normalizarRuta("/api/grupos")).isEqualTo("/api/grupos");
        assertThat(RegistroFallos.normalizarRuta(null)).isEqualTo("(desconocida)");
    }

    @Test
    @DisplayName("la huella es estable y distingue origen, tipo, lugar y línea")
    void huella() {
        String a = RegistroFallos.huella(Origen.SERVIDOR, "NPE", "GET /api/x", "Clase.metodo");
        assertThat(RegistroFallos.huella(Origen.SERVIDOR, "NPE", "GET /api/x", "Clase.metodo")).isEqualTo(a);
        assertThat(RegistroFallos.huella(Origen.CLIENTE, "NPE", "GET /api/x", "Clase.metodo")).isNotEqualTo(a);
        assertThat(RegistroFallos.huella(Origen.SERVIDOR, "NPE", "GET /api/y", "Clase.metodo")).isNotEqualTo(a);
        assertThat(a).hasSize(32);
    }

    @Test
    @DisplayName("recortar deja los textos cortos igual y corta los largos con elipsis")
    void recorta() {
        assertThat(RegistroFallos.recortar("hola", 10)).isEqualTo("hola");
        assertThat(RegistroFallos.recortar("x".repeat(20), 10)).hasSize(10).endsWith("…");
        assertThat(RegistroFallos.recortar(null, 10)).isNull();
    }

    @Test
    @DisplayName("si Mongo falla, registrar no lanza: un error no se convierte en dos")
    void nuncaLanza() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.upsert(any(Query.class), any(Update.class), eq(com.huecko.backend.mongo.document.FalloRegistrado.class)))
                .thenThrow(new IllegalStateException("mongo caído"));
        RegistroFallos registro = new RegistroFallos(mongo, mock(FalloRegistradoRepository.class));

        assertThatCode(() -> registro.registrarServidor(new RuntimeException("x"), "GET", "/api/x"))
                .doesNotThrowAnyException();
        assertThatCode(() -> registro.registrarCliente("TypeError", "x", null, "/", "UA"))
                .doesNotThrowAnyException();
    }
}
