package com.huecko.backend.tiemporeal;

import com.huecko.backend.tiemporeal.dto.EventoTiempoReal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** RNF-05: publicación de eventos hacia el topic del grupo. */
class NotificadorTiempoRealTest {

    private static final UUID GRUPO = UUID.randomUUID();

    private SimpMessagingTemplate plantilla;
    private NotificadorTiempoReal notificador;

    @BeforeEach
    void setUp() {
        plantilla = mock(SimpMessagingTemplate.class);
        notificador = new NotificadorTiempoReal(plantilla);
    }

    @Test
    @DisplayName("Publica en el destino del grupo y conserva tipo y datos")
    void publicaEnElDestinoDelGrupo() {
        notificador.aGrupo(GRUPO, EventoTiempoReal.Tipo.PLAN_CONFIRMADO, Map.of("planId", "p-1"));

        ArgumentCaptor<String> destino = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> cuerpo = ArgumentCaptor.forClass(Object.class);
        verify(plantilla).convertAndSend(destino.capture(), cuerpo.capture());

        assertThat(destino.getValue()).isEqualTo(Destinos.grupo(GRUPO));
        assertThat(cuerpo.getValue()).isInstanceOf(EventoTiempoReal.class);

        EventoTiempoReal evento = (EventoTiempoReal) cuerpo.getValue();
        assertThat(evento.tipo()).isEqualTo(EventoTiempoReal.Tipo.PLAN_CONFIRMADO);
        assertThat(evento.grupoId()).isEqualTo(GRUPO);
        assertThat(evento.datos()).containsEntry("planId", "p-1");
        assertThat(evento.ocurridoEn()).isNotNull();
    }

    @Test
    @DisplayName("Si el broker falla, la operacion de negocio no se entera")
    void elFalloDelBrokerNoSePropaga() {
        doThrow(new IllegalStateException("broker caído"))
                .when(plantilla).convertAndSend(anyString(), any(Object.class));

        assertThatCode(() -> notificador.aGrupo(
                GRUPO, EventoTiempoReal.Tipo.RETRASO_REPORTADO, Map.of("minutos", 10)))
                .doesNotThrowAnyException();
    }
}
