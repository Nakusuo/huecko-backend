package com.huecko.backend.admin.service;

import com.huecko.backend.admin.dto.GrupoAdminResponse;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Cálculo puro de la lista de grupos del panel, como `CalculoResumen`. */
final class ListadoGrupos {

    private ListadoGrupos() {
    }

    /** Los que se movieron hace menos primero; los que nunca se movieron, al final. */
    static List<GrupoAdminResponse> listar(CalculoResumen.Datos d) {
        Map<String, List<MiembroGrupo>> miembros = d.miembros().stream()
                .collect(Collectors.groupingBy(m -> m.getGrupo().getId().toString()));
        Map<String, List<Plan>> planes = d.planes().stream()
                .collect(Collectors.groupingBy(p -> p.getGrupo().getId().toString()));

        Map<String, Long> imprevistos = new HashMap<>();
        Map<String, Instant> actividad = new HashMap<>();
        d.planes().forEach(p -> anotar(actividad, p.getGrupo().getId().toString(), p.getCreadoEn()));
        d.retrasos().forEach(r -> {
            imprevistos.merge(r.getGrupoId(), 1L, Long::sum);
            anotar(actividad, r.getGrupoId(), r.getActualizadoEn());
        });
        d.ausencias().forEach(a -> {
            imprevistos.merge(a.getGrupoId(), 1L, Long::sum);
            anotar(actividad, a.getGrupoId(), a.getReportadoEn());
        });

        return d.grupos().stream()
                .map(g -> fila(g, miembros.getOrDefault(g.getId().toString(), List.of()),
                        planes.getOrDefault(g.getId().toString(), List.of()),
                        imprevistos.getOrDefault(g.getId().toString(), 0L),
                        actividad.get(g.getId().toString())))
                .sorted(Comparator.comparing(GrupoAdminResponse::ultimaActividad,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private static GrupoAdminResponse fila(Grupo g, List<MiembroGrupo> miembros, List<Plan> planes,
                                           long imprevistos, Instant ultimaActividad) {
        List<String> organizadores = miembros.stream()
                .filter(m -> m.getRol() == MiembroGrupo.Rol.ORGANIZADOR)
                .map(m -> m.getUsuario().getNombre())
                .sorted()
                .toList();

        Map<Plan.Estado, Long> porEstado = new EnumMap<>(Plan.Estado.class);
        for (Plan.Estado e : Plan.Estado.values()) {
            porEstado.put(e, 0L);
        }
        planes.forEach(p -> porEstado.merge(p.getEstado(), 1L, Long::sum));

        return new GrupoAdminResponse(g.getId().toString(), g.getNombre(), g.getCreadoEn(), organizadores,
                miembros.size(), g.getUmbralDisponibilidad(), planes.size(), porEstado, imprevistos,
                ultimaActividad);
    }

    private static void anotar(Map<String, Instant> ultima, String grupoId, Instant cuando) {
        if (grupoId != null && cuando != null) {
            ultima.merge(grupoId, cuando, (a, b) -> a.isAfter(b) ? a : b);
        }
    }
}
