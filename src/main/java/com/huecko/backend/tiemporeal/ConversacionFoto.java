package com.huecko.backend.tiemporeal;

import java.util.UUID;

/**
 * Alguien reaccionó a una foto del álbum o la comentó. Solo un comentario
 * avisa (a quien subió la foto); una reacción solo refresca los contadores.
 *
 * @param comentario si fue un comentario nuevo; si no, cambiaron las reacciones
 */
public record ConversacionFoto(UUID grupoId, UUID planId, UUID fotoId, UUID autorFotoId, UUID usuarioId,
                               String nombre, String titulo, boolean comentario) {
}
