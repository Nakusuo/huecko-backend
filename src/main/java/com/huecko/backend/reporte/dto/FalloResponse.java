package com.huecko.backend.reporte.dto;

import com.huecko.backend.mongo.document.EstadoRevision;
import com.huecko.backend.mongo.document.FalloRegistrado;

import java.time.Instant;

public record FalloResponse(String id, FalloRegistrado.Origen origen, String tipo, String mensaje, String ubicacion,
                            String traza, String navegador, long ocurrencias, Instant primeraVez, Instant ultimaVez,
                            EstadoRevision estado, boolean reabierto) {

    public static FalloResponse from(FalloRegistrado f) {
        return new FalloResponse(f.getId(), f.getOrigen(), f.getTipo(), f.getMensaje(), f.getUbicacion(),
                f.getTraza(), f.getNavegador(), f.getOcurrencias(), f.getPrimeraVez(), f.getUltimaVez(),
                f.getEstado(), f.isReabierto());
    }
}
