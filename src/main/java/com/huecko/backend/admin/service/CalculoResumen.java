package com.huecko.backend.admin.service;

import com.huecko.backend.admin.dto.ResumenAdminResponse;
import com.huecko.backend.admin.dto.ResumenAdminResponse.Semana;
import com.huecko.backend.mongo.document.AlertaRetraso;
import com.huecko.backend.mongo.document.Ausencia;
import com.huecko.backend.mongo.document.BloqueHorario;
import com.huecko.backend.mongo.document.VotacionExpres;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.entity.VotoVentana;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cálculo puro del resumen: recibe las filas y el instante actual, y no toca
 * ninguna base. Así se prueba sin levantar Postgres ni Mongo.
 *
 * Se trabaja en memoria porque el volumen de un proyecto así es pequeño. Si
 * creciera, cada cifra pasaría a ser una consulta agregada en su repositorio.
 */
final class CalculoResumen {

    static final int SEMANAS = 8;

    /** Todo lo que el cálculo necesita, ya leído de las bases. */
    record Datos(List<Usuario> usuarios, List<Grupo> grupos, List<MiembroGrupo> miembros, List<Plan> planes,
                 List<VotoVentana> votos, List<BloqueHorario> bloques, List<AlertaRetraso> retrasos,
                 List<Ausencia> ausencias, List<VotacionExpres> votaciones, int diasRetencion) {
    }

    private CalculoResumen() {
    }

    static ResumenAdminResponse calcular(Datos d, Instant ahora, ZoneId zona) {
        Instant hace7 = ahora.minus(Duration.ofDays(7));
        Instant hace30 = ahora.minus(Duration.ofDays(30));

        Set<String> admins = d.usuarios().stream()
                .filter(u -> u.getRolSistema() == Usuario.RolSistema.ADMIN)
                .map(u -> u.getId().toString())
                .collect(Collectors.toSet());
        List<Usuario> cuentas = d.usuarios().stream()
                .filter(u -> !admins.contains(u.getId().toString()))
                .toList();

        return new ResumenAdminResponse(
                ahora,
                usuarios(d, cuentas, admins.size(), hace7, hace30),
                grupos(d, hace30),
                planes(d),
                imprevistos(d),
                semanas(d, cuentas, ahora, zona));
    }

    private static ResumenAdminResponse.Usuarios usuarios(Datos d, List<Usuario> cuentas, int admins,
                                                          Instant hace7, Instant hace30) {
        long nuevos = cuentas.stream().filter(u -> despuesDe(u.getCreadoEn(), hace7)).count();

        // Activa = hizo algo en la app en los últimos 30 días (ver ActividadReciente).
        Set<String> activos = ActividadReciente.ultimaPorUsuario(d).entrySet().stream()
                .filter(e -> despuesDe(e.getValue(), hace30))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(HashSet::new));

        Set<String> conHorario = d.bloques().stream()
                .filter(b -> b.getEstado() == BloqueHorario.Estado.CONFIRMADO)
                .map(BloqueHorario::getUsuarioId)
                .collect(Collectors.toSet());

        Set<String> ids = cuentas.stream().map(u -> u.getId().toString()).collect(Collectors.toSet());
        activos.retainAll(ids);
        conHorario.retainAll(ids);

        return new ResumenAdminResponse.Usuarios(cuentas.size(), nuevos, activos.size(), conHorario.size(), admins);
    }

    private static ResumenAdminResponse.Grupos grupos(Datos d, Instant hace30) {
        Set<String> activos = new HashSet<>();
        d.planes().stream().filter(p -> despuesDe(p.getCreadoEn(), hace30))
                .forEach(p -> activos.add(p.getGrupo().getId().toString()));
        d.retrasos().stream().filter(r -> despuesDe(r.getActualizadoEn(), hace30))
                .forEach(r -> activos.add(r.getGrupoId()));
        d.ausencias().stream().filter(a -> despuesDe(a.getReportadoEn(), hace30))
                .forEach(a -> activos.add(a.getGrupoId()));

        Set<String> conPlanes = d.planes().stream()
                .map(p -> p.getGrupo().getId().toString())
                .collect(Collectors.toSet());

        long total = d.grupos().size();
        double miembrosMedio = total == 0 ? 0 : redondear((double) d.miembros().size() / total);
        long sinPlanes = d.grupos().stream().filter(g -> !conPlanes.contains(g.getId().toString())).count();

        return new ResumenAdminResponse.Grupos(total, activos.size(), miembrosMedio, sinPlanes);
    }

    private static ResumenAdminResponse.Planes planes(Datos d) {
        Map<Plan.Estado, Long> porEstado = contar(d.planes(), Plan::getEstado, Plan.Estado.class);
        long confirmados = porEstado.get(Plan.Estado.CONFIRMADO);
        long cancelados = porEstado.get(Plan.Estado.CANCELADO);
        Double tasa = confirmados + cancelados == 0 ? null
                : redondear((double) confirmados / (confirmados + cancelados));
        return new ResumenAdminResponse.Planes(d.planes().size(), porEstado, tasa);
    }

    private static ResumenAdminResponse.Imprevistos imprevistos(Datos d) {
        long criticas = d.ausencias().stream().filter(Ausencia::isCritica).count();
        Double minutosMedio = d.retrasos().isEmpty() ? null
                : redondear(d.retrasos().stream().mapToInt(AlertaRetraso::getMinutosEstimados).average().orElse(0));

        long abiertas = d.votaciones().stream().filter(v -> v.getEstado() == VotacionExpres.Estado.ABIERTA).count();
        List<VotacionExpres> cerradas = d.votaciones().stream()
                .filter(v -> v.getEstado() == VotacionExpres.Estado.CERRADA && v.getResultado() != null)
                .toList();

        return new ResumenAdminResponse.Imprevistos(
                d.ausencias().size(), criticas, d.retrasos().size(), minutosMedio,
                abiertas, cerradas.size(),
                contar(cerradas, VotacionExpres::getResultado, VotacionExpres.Opcion.class),
                d.diasRetencion());
    }

    /** Las últimas `SEMANAS` semanas, de la más antigua a la actual, todas presentes aunque estén a cero. */
    private static List<Semana> semanas(Datos d, List<Usuario> cuentas, Instant ahora, ZoneId zona) {
        LocalDate lunesActual = ahora.atZone(zona).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<Semana> semanas = new ArrayList<>(SEMANAS);
        for (int i = SEMANAS - 1; i >= 0; i--) {
            LocalDate inicio = lunesActual.minusWeeks(i);
            Instant desde = inicio.atStartOfDay(zona).toInstant();
            Instant hasta = inicio.plusWeeks(1).atStartOfDay(zona).toInstant();

            long altas = cuentas.stream().filter(u -> entre(u.getCreadoEn(), desde, hasta)).count();
            long propuestos = d.planes().stream().filter(p -> entre(p.getCreadoEn(), desde, hasta)).count();
            long confirmados = d.planes().stream()
                    .filter(p -> p.getEstado() == Plan.Estado.CONFIRMADO && entre(p.getCerradoEn(), desde, hasta))
                    .count();
            semanas.add(new Semana(inicio, altas, propuestos, confirmados));
        }
        return semanas;
    }

    /** Un contador por cada valor del enum, también los que están a cero. */
    private static <T, E extends Enum<E>> Map<E, Long> contar(List<T> filas, Function<T, E> clave, Class<E> tipo) {
        Map<E, Long> cuenta = new EnumMap<>(tipo);
        for (E valor : tipo.getEnumConstants()) {
            cuenta.put(valor, 0L);
        }
        filas.forEach(f -> cuenta.merge(clave.apply(f), 1L, Long::sum));
        return cuenta;
    }

    private static boolean despuesDe(Instant instante, Instant limite) {
        return instante != null && !instante.isBefore(limite);
    }

    private static boolean entre(Instant instante, Instant desde, Instant hasta) {
        return instante != null && !instante.isBefore(desde) && instante.isBefore(hasta);
    }

    private static double redondear(double valor) {
        return Math.round(valor * 100) / 100.0;
    }
}
