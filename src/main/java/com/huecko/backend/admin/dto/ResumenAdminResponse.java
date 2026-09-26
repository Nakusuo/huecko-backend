package com.huecko.backend.admin.dto;

import com.huecko.backend.mongo.document.VotacionExpres;
import com.huecko.backend.postgres.entity.Plan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Métricas agregadas de la plataforma para el panel de administración.
 *
 * Solo cifras: ni nombres, ni agendas, ni el contenido de los planes. El admin
 * ve cómo se usa Huecko, no lo que hace cada persona.
 */
public record ResumenAdminResponse(
        Instant generadoEn,
        Usuarios usuarios,
        Grupos grupos,
        Planes planes,
        Imprevistos imprevistos,
        List<Semana> semanas
) {

    /** Sin contar a los administradores: no usan el producto, lo operan. */
    public record Usuarios(long total, long nuevos7d, long activos30d, long conHorario, long admins) {
    }

    public record Grupos(long total, long activos30d, double miembrosMedio, long sinPlanes) {
    }

    /**
     * `tasaConcrecion` = confirmados / (confirmados + cancelados), entre 0 y 1.
     * Los planes aún en votación no cuentan. Nula si ninguno se ha cerrado.
     */
    public record Planes(long total, Map<Plan.Estado, Long> porEstado, Double tasaConcrecion) {
    }

    /**
     * Las votaciones exprés cerradas se purgan a los `diasRetencion` días, así
     * que sus cifras son de ese periodo, no históricas.
     */
    public record Imprevistos(long ausencias, long ausenciasCriticas, long retrasos, Double minutosRetrasoMedio,
                              long votacionesAbiertas, long votacionesCerradasRecientes,
                              Map<VotacionExpres.Opcion, Long> resultados, int diasRetencion) {
    }

    /** Semana que empieza el lunes `inicio`, en la zona de los grupos. */
    public record Semana(LocalDate inicio, long altas, long planesPropuestos, long planesConfirmados) {
    }
}
