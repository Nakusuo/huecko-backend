package com.huecko.backend.tiemporeal;

import com.huecko.backend.tiemporeal.dto.EventoTiempoReal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Avisos de cambios en un grupo. Igual que con los planes, se emiten después
 * del commit: un aviso de alguien que «entró» y luego no quedó guardado no se
 * puede deshacer.
 */
@Component
@RequiredArgsConstructor
public class NotificacionesGrupoListener {

    private final NotificadorTiempoReal notificador;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alUnirseAlguien(MiembroUnido evento) {
        notificador.aGrupo(evento.grupoId(), EventoTiempoReal.Tipo.MIEMBRO_UNIDO,
                Map.of("usuarioId", evento.usuarioId().toString(), "nombre", evento.nombre()));
    }
}
