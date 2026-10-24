package com.huecko.backend.tiemporeal;

import java.util.UUID;

/** Alguien marcó «Llegué» en un plan. El grupo lo ve en vivo. */
public record LlegadaRegistrada(UUID grupoId, UUID planId, String titulo, UUID usuarioId, String nombre,
                                boolean aTiempo) {
}
