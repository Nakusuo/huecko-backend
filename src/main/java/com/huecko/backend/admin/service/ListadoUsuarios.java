package com.huecko.backend.admin.service;

import com.huecko.backend.admin.dto.UsuarioAdminResponse;
import com.huecko.backend.postgres.entity.Usuario;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Cálculo puro de la lista de cuentas del panel, como `CalculoResumen`. */
final class ListadoUsuarios {

    private ListadoUsuarios() {
    }

    /** Las más recientes primero: suelen ser las que se buscan. */
    static List<UsuarioAdminResponse> listar(CalculoResumen.Datos d) {
        Map<String, Long> grupos = d.miembros().stream()
                .collect(Collectors.groupingBy(m -> m.getUsuario().getId().toString(), Collectors.counting()));
        Map<String, Long> planes = d.planes().stream()
                .collect(Collectors.groupingBy(p -> p.getCreadoPor().getId().toString(), Collectors.counting()));
        Map<String, Instant> actividad = ActividadReciente.ultimaPorUsuario(d);

        Function<Usuario, UsuarioAdminResponse> aFila = u -> {
            String id = u.getId().toString();
            return new UsuarioAdminResponse(id, u.getNombre(), u.getEmail(), u.getRolSistema(), u.getCreadoEn(),
                    u.isSuspendido(), grupos.getOrDefault(id, 0L), planes.getOrDefault(id, 0L), actividad.get(id));
        };

        return d.usuarios().stream()
                .sorted(Comparator.comparing(Usuario::getCreadoEn, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(aFila)
                .toList();
    }
}
