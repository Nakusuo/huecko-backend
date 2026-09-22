package com.huecko.backend.tiemporeal;

import com.huecko.backend.plan.event.PlanCerradoEvent;
import com.huecko.backend.tiemporeal.dto.EventoTiempoReal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RF-11: «notificar a todos los integrantes del grupo la fecha/hora final
 * confirmada de un evento».
 *
 * {@code AFTER_COMMIT} es la parte que importa: el aviso sale solo cuando la
 * transacción ya guardó el plan. Con un listener normal el mensaje se enviaría
 * dentro de la transacción y un rollback posterior dejaría al grupo con una
 * hora confirmada que la base nunca llegó a tener — y un WebSocket no se puede
 * deshacer.
 */
@Component
@RequiredArgsConstructor
public class NotificacionesPlanListener {

    private final NotificadorTiempoReal notificador;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCerrarLaVotacion(PlanCerradoEvent evento) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("planId", evento.planId().toString());
        datos.put("titulo", evento.titulo());

        if (evento.confirmado()) {
            // Un mapa inmutable no admite nulos, y `lugar` es opcional (RF-08).
            if (evento.lugar() != null) {
                datos.put("lugar", evento.lugar());
            }
            datos.put("fecha", evento.fecha().toString());
            datos.put("horaInicio", evento.horaInicio().toString());
            datos.put("horaFin", evento.horaFin().toString());
            notificador.aGrupo(evento.grupoId(), EventoTiempoReal.Tipo.PLAN_CONFIRMADO, datos);
            return;
        }

        // Fuera de RF-11: el documento solo pide avisar de la confirmación.
        // Se avisa igual porque un plan que se cancela en silencio deja al
        // grupo esperando una fecha que ya no va a llegar.
        datos.put("motivo", "La votación cerró sin ningún voto");
        notificador.aGrupo(evento.grupoId(), EventoTiempoReal.Tipo.PLAN_CANCELADO, datos);
    }
}
