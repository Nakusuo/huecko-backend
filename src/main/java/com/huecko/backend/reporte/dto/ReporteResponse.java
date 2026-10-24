package com.huecko.backend.reporte.dto;

import com.huecko.backend.mongo.document.EstadoRevision;
import com.huecko.backend.mongo.document.ReporteUsuario;
import com.huecko.backend.postgres.entity.Usuario;

import java.time.Instant;

/**
 * Un reporte para la bandeja del admin. `cuentaReportadaSuspendida` es el
 * estado actual de la cuenta señalada (nulo si no hay o ya no existe), y
 * `hayFotoQueRevisar` dice si sigue existiendo la foto en juego: la del álbum
 * en un reporte de FOTO, la de perfil en uno de CONDUCTA.
 */
public record ReporteResponse(String id, ReporteUsuario.Tipo tipo, String descripcion, String ruta, String navegador,
                              String autorNombre, String autorEmail, String cuentaReportadaNombre,
                              String cuentaReportadaEmail, Boolean cuentaReportadaSuspendida,
                              Boolean hayFotoQueRevisar,
                              EstadoRevision estado, Instant creadoEn, Instant actualizadoEn) {

    /** `cuenta` es la cuenta reportada, o nulo si el reporte no señala ninguna o ya no existe. */
    public static ReporteResponse from(ReporteUsuario r, Usuario cuenta, Boolean hayFotoQueRevisar) {
        return new ReporteResponse(r.getId(), r.getTipo(), r.getDescripcion(), r.getRuta(), r.getNavegador(),
                r.getAutorNombre(), r.getAutorEmail(), r.getCuentaReportadaNombre(), r.getCuentaReportadaEmail(),
                cuenta == null ? null : cuenta.isSuspendido(),
                hayFotoQueRevisar,
                r.getEstado(), r.getCreadoEn(), r.getActualizadoEn());
    }
}
