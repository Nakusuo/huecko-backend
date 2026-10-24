package com.huecko.backend.tiemporeal;

import java.util.UUID;

/**
 * Alguien entró a un grupo con el código de invitación. Se avisa al grupo en
 * vivo: con el código la entrada no la decide quien organiza persona a
 * persona, así que al menos todos se enteran en el momento.
 */
public record MiembroUnido(UUID grupoId, UUID usuarioId, String nombre) {
}
