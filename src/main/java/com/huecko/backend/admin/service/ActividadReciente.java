package com.huecko.backend.admin.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Último momento en que cada cuenta hizo algo en la app: proponer un plan,
 * votar, tocar su horario o avisar de un retraso o una ausencia. Entrar sin
 * hacer nada no deja rastro, así que no cuenta.
 */
final class ActividadReciente {

    private ActividadReciente() {
    }

    /** Id de usuario (texto) → su acción más reciente. Quien no hizo nada no aparece. */
    static Map<String, Instant> ultimaPorUsuario(CalculoResumen.Datos d) {
        Map<String, Instant> ultima = new HashMap<>();
        d.planes().forEach(p -> anotar(ultima, p.getCreadoPor().getId().toString(), p.getCreadoEn()));
        d.votos().forEach(v -> anotar(ultima, v.getUsuario().getId().toString(), v.getCreadoEn()));
        d.bloques().forEach(b -> anotar(ultima, b.getUsuarioId(), b.getActualizadoEn()));
        d.retrasos().forEach(r -> anotar(ultima, r.getUsuarioId(), r.getActualizadoEn()));
        d.ausencias().forEach(a -> anotar(ultima, a.getUsuarioId(), a.getReportadoEn()));
        return ultima;
    }

    private static void anotar(Map<String, Instant> ultima, String usuarioId, Instant cuando) {
        if (usuarioId == null || cuando == null) {
            return;
        }
        ultima.merge(usuarioId, cuando, (a, b) -> a.isAfter(b) ? a : b);
    }
}
