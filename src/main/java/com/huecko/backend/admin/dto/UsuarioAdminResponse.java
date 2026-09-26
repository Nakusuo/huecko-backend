package com.huecko.backend.admin.dto;

import com.huecko.backend.postgres.entity.Usuario;

import java.time.Instant;

/**
 * Una cuenta vista desde el panel: datos de la cuenta y cuánto la usa. Nada de
 * su horario ni de qué grupos o planes son.
 *
 * `ultimaActividad` es nula si nunca hizo nada (ver ActividadReciente).
 */
public record UsuarioAdminResponse(
        String id,
        String nombre,
        String email,
        Usuario.RolSistema rolSistema,
        Instant creadoEn,
        boolean suspendido,
        long grupos,
        long planesPropuestos,
        Instant ultimaActividad
) {
}
