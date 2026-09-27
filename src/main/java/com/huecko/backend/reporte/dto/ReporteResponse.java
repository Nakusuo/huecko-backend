package com.huecko.backend.reporte.dto;

import com.huecko.backend.mongo.document.EstadoRevision;
import com.huecko.backend.mongo.document.ReporteUsuario;

import java.time.Instant;

/**
 * Un reporte para la bandeja del admin. `cuentaReportadaSuspendida` es el estado
 * actual de esa cuenta (nulo si no es de CONDUCTA o la cuenta ya no existe).
 */
public record ReporteResponse(String id, ReporteUsuario.Tipo tipo, String descripcion, String ruta, String navegador,
                              String autorNombre, String autorEmail, String cuentaReportadaNombre,
                              String cuentaReportadaEmail, Boolean cuentaReportadaSuspendida,
                              EstadoRevision estado, Instant creadoEn, Instant actualizadoEn) {

    public static ReporteResponse from(ReporteUsuario r, Boolean suspendida) {
        return new ReporteResponse(r.getId(), r.getTipo(), r.getDescripcion(), r.getRuta(), r.getNavegador(),
                r.getAutorNombre(), r.getAutorEmail(), r.getCuentaReportadaNombre(), r.getCuentaReportadaEmail(),
                suspendida, r.getEstado(), r.getCreadoEn(), r.getActualizadoEn());
    }
}
