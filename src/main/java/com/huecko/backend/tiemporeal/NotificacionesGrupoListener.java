package com.huecko.backend.tiemporeal;

import com.huecko.backend.tiemporeal.dto.EventoTiempoReal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alAvisarQueEstaLibre(LibreAnunciado evento) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("usuarioId", evento.usuarioId().toString());
        datos.put("nombre", evento.nombre());
        datos.put("hasta", evento.hasta().toString());
        if (evento.nota() != null) {
            datos.put("nota", evento.nota());
        }
        evento.grupoIds().forEach(grupoId -> notificador.aGrupo(grupoId, EventoTiempoReal.Tipo.LIBRE_AHORA, datos));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCambiarElAlbum(AlbumCambiado evento) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("planId", evento.planId().toString());
        datos.put("titulo", evento.titulo());
        datos.put("usuarioId", evento.usuarioId().toString());
        datos.put("subidas", evento.subidas());
        if (evento.nombre() != null) {
            datos.put("nombre", evento.nombre());
        }
        notificador.aGrupo(evento.grupoId(), EventoTiempoReal.Tipo.ALBUM_ACTUALIZADO, datos);
    }
}
