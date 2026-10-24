package com.huecko.backend.usuario.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * La tarjeta de alguien tal como la ve otra persona de sus grupos.
 *
 * RNF-02: aquí no viaja nada de su horario. Y de sus logros solo salen las
 * insignias que tiene, nunca cuántas veces llegó tarde.
 */
public record PerfilPublicoResponse(
        UUID usuarioId,
        String nombre,
        String carrera,
        Integer ciclo,
        String bio,
        List<String> intereses,
        Instant fotoActualizadaEn,
        Instant enHueckoDesde,
        /** Nombres de los grupos que tienen en común; todos los suyos si miras tu propia tarjeta. */
        List<String> gruposEnComun,
        /** Planes a los que fueron los dos. Nulo si es tu propia tarjeta. */
        Integer planesJuntos,
        List<String> insignias
) {
}
