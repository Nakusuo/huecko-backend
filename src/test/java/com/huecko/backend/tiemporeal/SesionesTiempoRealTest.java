package com.huecko.backend.tiemporeal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La pertenencia a un grupo solo se comprueba al suscribirse. Sin cortar las
 * sesiones vivas, a quien sacaban del grupo (o suspendían, o le caducaba el
 * token) le seguían llegando las ausencias y los motivos de ese grupo.
 */
class SesionesTiempoRealTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BEA = UUID.randomUUID();
    private final Instant ahora = Instant.parse("2026-10-10T12:00:00Z");

    private SesionesTiempoReal sesiones;
    private WebSocketHandler manejador;

    @BeforeEach
    void setUp() {
        sesiones = new SesionesTiempoReal();
        manejador = sesiones.decorate(mock(WebSocketHandler.class));
    }

    private WebSocketSession abrir(String id) throws Exception {
        WebSocketSession sesion = mock(WebSocketSession.class);
        when(sesion.getId()).thenReturn(id);
        when(sesion.isOpen()).thenReturn(true);
        manejador.afterConnectionEstablished(sesion);
        return sesion;
    }

    @Test
    @DisplayName("al revocar el acceso de alguien se cierran todas sus sesiones y solo las suyas")
    void cierraLasDeEsaPersona() throws Exception {
        WebSocketSession movil = abrir("s1");
        WebSocketSession portatil = abrir("s2");
        WebSocketSession deBea = abrir("s3");
        sesiones.asociar("s1", ANA, ahora.plusSeconds(3600));
        sesiones.asociar("s2", ANA, ahora.plusSeconds(3600));
        sesiones.asociar("s3", BEA, ahora.plusSeconds(3600));

        sesiones.alRevocarAcceso(new AccesoRevocado(ANA));

        verify(movil).close(any(CloseStatus.class));
        verify(portatil).close(any(CloseStatus.class));
        verify(deBea, never()).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("cierra las sesiones cuyo token ya caducó")
    void cierraLasCaducadas() throws Exception {
        WebSocketSession vieja = abrir("s1");
        WebSocketSession vigente = abrir("s2");
        sesiones.asociar("s1", ANA, ahora.minusSeconds(1));
        sesiones.asociar("s2", BEA, ahora.plusSeconds(60));

        sesiones.cerrarCaducadas(ahora);

        verify(vieja).close(any(CloseStatus.class));
        verify(vigente, never()).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("una sesión ya cerrada se olvida y no se vuelve a tocar")
    void olvidaLasCerradas() throws Exception {
        WebSocketSession sesion = abrir("s1");
        sesiones.asociar("s1", ANA, ahora.minusSeconds(1));

        manejador.afterConnectionClosed(sesion, CloseStatus.NORMAL);
        sesiones.cerrarCaducadas(ahora);

        verify(sesion, never()).close(any(CloseStatus.class));
    }
}
