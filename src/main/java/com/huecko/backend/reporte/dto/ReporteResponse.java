package com.huecko.backend.reporte.dto;

import com.huecko.backend.mongo.document.EstadoRevision;
import com.huecko.backend.mongo.document.ReporteUsuario;
import com.huecko.backend.postgres.entity.Usuario;

import java.time.Instant;

/**
 * Un reporte para la bandeja del admin. `cuentaReportadaSuspendida` y
 * `cuentaReportadaConFoto` son el estado actual de esa cuenta (nulos si no es
 * de CONDUCTA o la cuenta ya no existe).
 */
public record ReporteResponse(String id, ReporteUsuario.Tipo tipo, String descripcion, String ruta, String navegador,
                              String autorNombre, String autorEmail, String cuentaReportadaNombre,
                              String cuentaReportadaEmail, Boolean cuentaReportadaSuspendida,
                              Boolean cuentaReportadaConFoto,
                              EstadoRevision estado, Instant creadoEn, Instant actualizadoEn) {

    /** `cuenta` es la cuenta reportada, o nulo si el reporte no señala ninguna o ya no existe. */
    public static ReporteResponse from(ReporteUsuario r, Usuario cuenta) {
        return new ReporteResponse(r.getId(), r.getTipo(), r.getDescripcion(), r.getRuta(), r.getNavegador(),
                r.getAutorNombre(), r.getAutorEmail(), r.getCuentaReportadaNombre(), r.getCuentaReportadaEmail(),
                cuenta == null ? null : cuenta.isSuspendido(),
                cuenta == null ? null : cuenta.getFotoActualizadaEn() != null,
                r.getEstado(), r.getCreadoEn(), r.getActualizadoEn());
    }
}
