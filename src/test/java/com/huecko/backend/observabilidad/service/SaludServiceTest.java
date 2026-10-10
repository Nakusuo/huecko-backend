package com.huecko.backend.observabilidad.service;

import com.huecko.backend.mongo.repository.VotacionExpresRepository;
import com.huecko.backend.observabilidad.dto.SaludResponse;
import com.huecko.backend.observabilidad.dto.SaludResponse.Estado;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.ia.VigiaIA;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SaludServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final SimpUserRegistry conectados = mock(SimpUserRegistry.class);
    private final PlanRepository planes = mock(PlanRepository.class);
    private final RegistroTareas tareas = new RegistroTareas();

    private final VigiaIA vigiaIA = mock(VigiaIA.class);

    private SaludService servicio() {
        when(vigiaIA.ultimo()).thenReturn(new VigiaIA.Comprobacion(VigiaIA.Estado.DESACTIVADA, null, null));
        when(mongo.executeCommand(any(Document.class))).thenReturn(new Document("ok", 1));
        return new SaludService(jdbc, mongo, conectados, tareas, planes, mock(VotacionExpresRepository.class),
                new MockEnvironment(), vigiaIA, "1.0.0");
    }

    @Test
    @DisplayName("todo responde: sistema OK")
    void todoBien() {
        SaludService s = servicio();
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        SaludResponse r = s.salud();

        assertThat(r.estado()).isEqualTo(Estado.OK);
        assertThat(r.componentes()).extracting(SaludResponse.Componente::clave)
                .containsExactly("postgres", "mongo", "tiempoReal", "ia");
        assertThat(r.aplicacion().version()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("la IA sin respuesta degrada el sistema pero no lo tumba: la app sigue con reglas")
    void iaSinRespuesta() {
        SaludService s = servicio();
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(vigiaIA.ultimo()).thenReturn(
                new VigiaIA.Comprobacion(VigiaIA.Estado.SIN_RESPUESTA, java.time.Instant.now(), null));

        SaludResponse r = s.salud();

        assertThat(r.estado()).isEqualTo(Estado.DEGRADADO);
        assertThat(r.componentes()).filteredOn(c -> c.clave().equals("ia"))
                .singleElement().extracting(SaludResponse.Componente::estado).isEqualTo(Estado.DEGRADADO);
    }

    @Test
    @DisplayName("Postgres caído: el sistema está caído y no se filtra el mensaje del driver")
    void postgresCaido() {
        SaludService s = servicio();
        when(jdbc.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new CannotGetJdbcConnectionException("jdbc:postgresql://user:clave@host"));

        SaludResponse r = s.salud();

        assertThat(r.estado()).isEqualTo(Estado.CAIDO);
        SaludResponse.Componente pg = r.componentes().get(0);
        assertThat(pg.estado()).isEqualTo(Estado.CAIDO);
        assertThat(pg.detalle()).doesNotContain("clave");
    }

    @Test
    @DisplayName("votaciones vencidas sin cerrar o fallos en la última pasada: la tarea queda degradada")
    void tareaDegradada() {
        SaludService s = servicio();
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(planes.findByEstadoAndPlazoVotacionLessThanEqual(eq(Plan.Estado.PROPUESTO), any()))
                .thenReturn(List.of(new Plan()));
        tareas.registrar(Tarea.CIERRE_VOTACIONES_EXPRES, Instant.now(), Duration.ofMillis(5), 0, 1, "boom");

        SaludResponse r = s.salud();

        assertThat(r.estado()).isEqualTo(Estado.DEGRADADO);
        assertThat(r.tareas().get(0).atrasadas()).isEqualTo(1);
        assertThat(r.tareas().get(1).estado()).isEqualTo(Estado.DEGRADADO);
        assertThat(r.tareas().get(1).ultimoError()).isEqualTo("boom");
    }
}
