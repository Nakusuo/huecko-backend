package com.huecko.backend.tiemporeal;

import java.util.UUID;

/**
 * Cambió el álbum de un plan: alguien subió fotos (`subidas` > 0, el grupo
 * recibe un aviso) o se borró alguna (`subidas` = 0, solo se refresca).
 */
public record AlbumCambiado(UUID grupoId, UUID planId, String titulo, UUID usuarioId, String nombre, int subidas) {
}
