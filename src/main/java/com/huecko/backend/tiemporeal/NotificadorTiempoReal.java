package com.huecko.backend.tiemporeal;

import com.huecko.backend.tiemporeal.dto.EventoTiempoReal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Única salida hacia el canal en tiempo real (RNF-05).
 *
 * Los servicios de negocio llaman aquí y no tocan {@link SimpMessagingTemplate}
 * directamente, para que el nombre del destino se construya siempre igual y
 * para poder cambiar el transporte sin recorrer los módulos.
 *
 * <b>Nunca lanza.</b> Una notificación que falla no puede tumbar la operación
 * que la originó: si el plan se confirmó y el aviso no sale, el plan sigue
 * confirmado. Lo contrario dejaría datos a medias por un fallo de mensajería.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificadorTiempoReal {

    private final SimpMessagingTemplate plantilla;

    public void aGrupo(UUID grupoId, EventoTiempoReal.Tipo tipo, Map<String, Object> datos) {
        aGrupo(EventoTiempoReal.de(tipo, grupoId, datos));
    }

    public void aGrupo(EventoTiempoReal evento) {
        try {
            plantilla.convertAndSend(Destinos.grupo(evento.grupoId()), evento);
        } catch (RuntimeException ex) {
            log.warn("No se pudo publicar {} en el grupo {}: {}",
                    evento.tipo(), evento.grupoId(), ex.getMessage());
        }
    }
}
