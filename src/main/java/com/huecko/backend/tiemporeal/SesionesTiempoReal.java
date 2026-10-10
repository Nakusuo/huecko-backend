package com.huecko.backend.tiemporeal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sesiones WebSocket abiertas, de quién son y hasta cuándo vale su token.
 *
 * La pertenencia a un grupo se comprueba al suscribirse
 * (SeguridadStompInterceptor), pero una suscripción viva no se vuelve a
 * mirar. Sin esto, a quien sacaban de un grupo, suspendían o le caducaba el
 * token le seguían llegando los eventos del grupo hasta que reconectaba.
 *
 * Cerrar la sesión basta: el cliente reconecta solo (lib/realtime.ts),
 * vuelve a autenticarse y a suscribirse, y ahí ya se aplican los permisos
 * actuales.
 */
@Component
public class SesionesTiempoReal implements WebSocketHandlerDecoratorFactory {

    private static final Logger log = LoggerFactory.getLogger(SesionesTiempoReal.class);
    private static final CloseStatus ACCESO_CAMBIADO =
            CloseStatus.POLICY_VIOLATION.withReason("Tus permisos han cambiado");

    private record Asociacion(UUID usuarioId, Instant caduca) {
    }

    private final Map<String, WebSocketSession> abiertas = new ConcurrentHashMap<>();
    private final Map<String, Asociacion> asociaciones = new ConcurrentHashMap<>();

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                abiertas.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                abiertas.remove(session.getId());
                asociaciones.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        };
    }

    /** Lo llama el CONNECT ya autenticado. El id de sesión STOMP es el de la sesión WebSocket. */
    public void asociar(String sesionId, UUID usuarioId, Instant caducaToken) {
        if (sesionId != null) {
            asociaciones.put(sesionId, new Asociacion(usuarioId, caducaToken));
        }
    }

    /** Tras el commit: si la transacción que sacó a alguien se deshace, no se corta a nadie. */
    @TransactionalEventListener(fallbackExecution = true)
    public void alRevocarAcceso(AccesoRevocado evento) {
        asociaciones.forEach((sesionId, asociacion) -> {
            if (asociacion.usuarioId().equals(evento.usuarioId())) {
                cerrar(sesionId);
            }
        });
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void cerrarCaducadasAhora() {
        cerrarCaducadas(Instant.now());
    }

    void cerrarCaducadas(Instant ahora) {
        asociaciones.forEach((sesionId, asociacion) -> {
            if (asociacion.caduca() != null && !asociacion.caduca().isAfter(ahora)) {
                cerrar(sesionId);
            }
        });
    }

    private void cerrar(String sesionId) {
        asociaciones.remove(sesionId);
        WebSocketSession sesion = abiertas.remove(sesionId);
        if (sesion == null || !sesion.isOpen()) {
            return;
        }
        try {
            sesion.close(ACCESO_CAMBIADO);
        } catch (IOException ex) {
            log.debug("No se pudo cerrar la sesión {}: {}", sesionId, ex.getMessage());
        }
    }
}
