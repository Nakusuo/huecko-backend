package com.huecko.backend.muro;

import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.muro.MuroResponse.Entrada;
import com.huecko.backend.muro.MuroResponse.Persona;
import com.huecko.backend.muro.MuroResponse.Tipo;
import com.huecko.backend.postgres.entity.Llegada;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.entity.VentanaPlan;
import com.huecko.backend.postgres.repository.FotoPlanRepository;
import com.huecko.backend.postgres.repository.FotoPlanRepository.FotoPlanResumen;
import com.huecko.backend.postgres.repository.LlegadaRepository;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * El muro del grupo: quién entró, qué planes se propusieron, confirmaron o
 * cancelaron, quién subió fotos y quiénes llegaron a cada plan.
 *
 * No hay una tabla de actividad: el muro se calcula de los datos de siempre,
 * así nunca se desincroniza (una foto borrada desaparece también del muro). Un
 * grupo tiene unas decenas de planes y como mucho sesenta fotos, así que se
 * recorre entero en cada petición.
 *
 * Por diseño no aparecen ni ausencias ni retrasos: son de cada quien, y el
 * muro es para celebrar lo que hace el grupo. Tampoco nada del horario (RNF-02).
 */
@Service
@RequiredArgsConstructor
public class MuroService {

    static final int POR_PAGINA = 20;
    /** Fotos de la misma persona al mismo plan con menos de esto entre sí: una sola entrada. */
    static final Duration TANDA = Duration.ofMinutes(10);
    private static final int VISTA_PREVIA = 4;

    private final MiembroGrupoRepository miembros;
    private final PlanRepository planes;
    private final FotoPlanRepository fotos;
    private final LlegadaRepository llegadas;
    private final UsuarioRepository usuarios;

    @Transactional(readOnly = true)
    public MuroResponse muro(UUID usuarioId, UUID grupoId, Instant antes) {
        if (!miembros.existsByGrupo_IdAndUsuario_Id(grupoId, usuarioId)) {
            throw new NotFoundException("El grupo no existe o no perteneces a él");
        }
        List<MiembroGrupo> integrantes = miembros.findByGrupoIdConUsuario(grupoId);
        List<Plan> planesDelGrupo = planes.findByGrupoConVentanas(grupoId);
        List<FotoPlanResumen> fotosDelGrupo = fotos.findByGrupoIdOrderBySubidaEnAsc(grupoId);
        List<Llegada> llegadasDelGrupo = planesDelGrupo.isEmpty() ? List.of()
                : llegadas.deLosPlanes(planesDelGrupo.stream().map(Plan::getId).toList());

        Map<UUID, Usuario> gente = gente(integrantes, planesDelGrupo, fotosDelGrupo, llegadasDelGrupo);
        Map<UUID, Plan> porId = planesDelGrupo.stream().collect(Collectors.toMap(Plan::getId, p -> p));

        List<Entrada> todas = new ArrayList<>();
        for (MiembroGrupo m : integrantes) {
            if (m.getUnidoEn() != null) {
                todas.add(entrada(Tipo.MIEMBRO_UNIDO, m.getUnidoEn(), persona(gente, m.getUsuario().getId()), null));
            }
        }
        for (Plan p : planesDelGrupo) {
            todas.add(entrada(Tipo.PLAN_PROPUESTO, p.getCreadoEn(), persona(gente, p.getCreadoPor().getId()), p));
            if (p.getCerradoEn() != null && p.getEstado() == Plan.Estado.CONFIRMADO) {
                todas.add(entrada(Tipo.PLAN_CONFIRMADO, p.getCerradoEn(), null, p));
            } else if (p.getCerradoEn() != null && p.getEstado() == Plan.Estado.CANCELADO) {
                todas.add(entrada(Tipo.PLAN_CANCELADO, p.getCerradoEn(), null, p));
            }
        }
        todas.addAll(tandasDeFotos(fotosDelGrupo, porId, gente));
        todas.addAll(llegadasPorPlan(llegadasDelGrupo, porId, gente));

        List<Entrada> ordenadas = todas.stream()
                .filter(e -> e.ocurridoEn() != null && (antes == null || e.ocurridoEn().isBefore(antes)))
                .sorted(Comparator.comparing(Entrada::ocurridoEn).reversed())
                .toList();
        List<Entrada> pagina = ordenadas.stream().limit(POR_PAGINA).toList();
        Instant siguiente = ordenadas.size() > POR_PAGINA ? pagina.get(pagina.size() - 1).ocurridoEn() : null;
        return new MuroResponse(pagina, siguiente);
    }

    /** Fotos seguidas de la misma persona en el mismo plan, juntas. */
    private static List<Entrada> tandasDeFotos(List<FotoPlanResumen> todas, Map<UUID, Plan> planes,
                                               Map<UUID, Usuario> gente) {
        List<Entrada> entradas = new ArrayList<>();
        Map<String, List<FotoPlanResumen>> porPersonaYPlan = todas.stream().collect(Collectors.groupingBy(
                f -> f.getPlanId() + "/" + f.getAutorId(), LinkedHashMap::new, Collectors.toList()));
        for (List<FotoPlanResumen> suyas : porPersonaYPlan.values()) {
            List<FotoPlanResumen> tanda = new ArrayList<>();
            for (FotoPlanResumen foto : suyas) {
                if (!tanda.isEmpty() && Duration.between(tanda.get(tanda.size() - 1).getSubidaEn(), foto.getSubidaEn())
                        .compareTo(TANDA) > 0) {
                    entradas.add(fotos(tanda, planes, gente));
                    tanda = new ArrayList<>();
                }
                tanda.add(foto);
            }
            if (!tanda.isEmpty()) {
                entradas.add(fotos(tanda, planes, gente));
            }
        }
        return entradas;
    }

    private static Entrada fotos(List<FotoPlanResumen> tanda, Map<UUID, Plan> planes, Map<UUID, Usuario> gente) {
        FotoPlanResumen primera = tanda.get(0);
        Plan plan = planes.get(primera.getPlanId());
        return new Entrada(Tipo.FOTOS_SUBIDAS, tanda.get(tanda.size() - 1).getSubidaEn(),
                persona(gente, primera.getAutorId()), primera.getPlanId(), plan == null ? null : plan.getTitulo(),
                null, null, tanda.size(),
                tanda.stream().limit(VISTA_PREVIA).map(FotoPlanResumen::getId).toList(), null);
    }

    /** Una entrada por plan con todas las personas que llegaron, en el orden en que llegaron. */
    private static List<Entrada> llegadasPorPlan(List<Llegada> todas, Map<UUID, Plan> planes, Map<UUID, Usuario> gente) {
        Map<UUID, List<Llegada>> porPlan = todas.stream()
                .sorted(Comparator.comparing(Llegada::getLlegoEn))
                .collect(Collectors.groupingBy(l -> l.getId().getPlanId(), LinkedHashMap::new, Collectors.toList()));
        List<Entrada> entradas = new ArrayList<>();
        porPlan.forEach((planId, lista) -> {
            Plan plan = planes.get(planId);
            entradas.add(new Entrada(Tipo.LLEGARON, lista.get(lista.size() - 1).getLlegoEn(), null, planId,
                    plan == null ? null : plan.getTitulo(), null, null, lista.size(), null,
                    lista.stream().map(l -> persona(gente, l.getId().getUsuarioId())).toList()));
        });
        return entradas;
    }

    private static Entrada entrada(Tipo tipo, Instant cuando, Persona actor, Plan plan) {
        VentanaPlan fecha = plan == null ? null : plan.getVentanaConfirmada();
        return new Entrada(tipo, cuando, actor, plan == null ? null : plan.getId(), plan == null ? null : plan.getTitulo(),
                fecha == null ? null : fecha.getFecha(), fecha == null ? null : fecha.getHoraInicio(),
                null, null, null);
    }

    /**
     * Toda la gente que aparece, con una consulta para quienes ya no están en
     * el grupo (siguen saliendo en lo que hicieron).
     */
    private Map<UUID, Usuario> gente(List<MiembroGrupo> integrantes, List<Plan> planes, List<FotoPlanResumen> fotos,
                                     List<Llegada> llegadas) {
        Map<UUID, Usuario> gente = new HashMap<>();
        integrantes.forEach(m -> gente.put(m.getUsuario().getId(), m.getUsuario()));
        Set<UUID> faltan = new HashSet<>();
        planes.forEach(p -> faltan.add(p.getCreadoPor().getId()));
        fotos.forEach(f -> faltan.add(f.getAutorId()));
        llegadas.forEach(l -> faltan.add(l.getId().getUsuarioId()));
        faltan.removeAll(gente.keySet());
        if (!faltan.isEmpty()) {
            usuarios.findAllById(faltan).forEach(u -> gente.put(u.getId(), u));
        }
        return gente;
    }

    private static Persona persona(Map<UUID, Usuario> gente, UUID id) {
        Usuario u = gente.get(id);
        return new Persona(id, u == null ? "Alguien" : u.getNombre(), u == null ? null : u.getFotoActualizadaEn());
    }
}
