package com.huecko.backend.usuario.service;

import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.mongo.document.AlertaRetraso;
import com.huecko.backend.mongo.document.Ausencia;
import com.huecko.backend.mongo.repository.AlertaRetrasoRepository;
import com.huecko.backend.mongo.repository.AusenciaRepository;
import com.huecko.backend.llegada.service.LlegadaService;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.Llegada;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.entity.VentanaPlan;
import com.huecko.backend.postgres.repository.GrupoRepository;
import com.huecko.backend.postgres.repository.LlegadaRepository;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.usuario.dto.LogrosResponse;
import com.huecko.backend.usuario.dto.PerfilPublicoResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tarjeta de perfil y logros.
 *
 * Un plan «vivido» es uno confirmado de tus grupos que ya empezó y al que
 * fuiste. Si alguien marcó «Llegué» en ese plan, cuenta lo marcado: fuiste si
 * lo marcaste, y a tiempo si fue hasta diez minutos tarde. Si nadie lo usó
 * (planes de antes de que existiera), se aproxima: fuiste si no avisaste que
 * faltarías, y a tiempo si no avisaste de un retraso.
 */
@Service
public class PerfilPublicoService {

    /** De lo más fácil a lo más difícil; el frontend las pinta en este orden. */
    public enum Insignia { PRIMER_PLAN, HABITUAL, PUNTUAL, ORGANIZA }

    static final int PLANES_HABITUAL = 5;
    static final int PLANES_PUNTUAL = 3;
    static final int PROPUESTOS_ORGANIZA = 3;
    private static final String NO_EXISTE = "Esa persona no existe o no compartes ningún grupo con ella.";

    private final UsuarioRepository usuarios;
    private final GrupoRepository grupos;
    private final MiembroGrupoRepository miembros;
    private final PlanRepository planes;
    private final AusenciaRepository ausencias;
    private final AlertaRetrasoRepository retrasos;
    private final LlegadaRepository llegadas;
    private final Clock reloj;

    @Autowired
    public PerfilPublicoService(UsuarioRepository usuarios, GrupoRepository grupos, MiembroGrupoRepository miembros,
                                PlanRepository planes, AusenciaRepository ausencias, AlertaRetrasoRepository retrasos,
                                LlegadaRepository llegadas) {
        this(usuarios, grupos, miembros, planes, ausencias, retrasos, llegadas, Clock.systemUTC());
    }

    PerfilPublicoService(UsuarioRepository usuarios, GrupoRepository grupos, MiembroGrupoRepository miembros,
                         PlanRepository planes, AusenciaRepository ausencias, AlertaRetrasoRepository retrasos,
                         LlegadaRepository llegadas, Clock reloj) {
        this.usuarios = usuarios;
        this.grupos = grupos;
        this.miembros = miembros;
        this.planes = planes;
        this.ausencias = ausencias;
        this.retrasos = retrasos;
        this.llegadas = llegadas;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public LogrosResponse logros(UUID usuarioId) {
        Historial historial = historial(usuarioId);
        int propuestos = propuestos(usuarioId);
        return new LogrosResponse(historial.vividos().size(), historial.aTiempo(), propuestos,
                insignias(historial.vividos().size(), historial.aTiempo(), propuestos));
    }

    /**
     * La tarjeta de `usuarioId` vista por `quienMira`. Sin un grupo en común se
     * responde como si no existiera: así no se puede usar para averiguar qué
     * ids son cuentas reales.
     */
    @Transactional(readOnly = true)
    public PerfilPublicoResponse perfil(UUID quienMira, UUID usuarioId) {
        boolean propia = quienMira.equals(usuarioId);
        List<Grupo> comunes = propia ? grupos.findByMiembro(usuarioId) : miembros.gruposEnComun(quienMira, usuarioId);
        if (!propia && comunes.isEmpty()) {
            throw new NotFoundException(NO_EXISTE);
        }
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow(() -> new NotFoundException(NO_EXISTE));

        Historial suyo = historial(usuarioId);
        Integer juntos = null;
        if (!propia) {
            Set<UUID> mios = historial(quienMira).vividos();
            juntos = (int) suyo.vividos().stream().filter(mios::contains).count();
        }

        return new PerfilPublicoResponse(usuario.getId(), usuario.getNombre(), usuario.getCarrera(),
                usuario.getCiclo(), usuario.getBio(), List.copyOf(usuario.getIntereses()),
                usuario.getFotoActualizadaEn(), usuario.getCreadoEn(),
                comunes.stream().map(Grupo::getNombre).toList(), juntos,
                insignias(suyo.vividos().size(), suyo.aTiempo(), propuestos(usuarioId)));
    }

    static List<String> insignias(int vividos, int aTiempo, int propuestos) {
        List<String> ganadas = new ArrayList<>();
        if (vividos >= 1) {
            ganadas.add(Insignia.PRIMER_PLAN.name());
        }
        if (vividos >= PLANES_HABITUAL) {
            ganadas.add(Insignia.HABITUAL.name());
        }
        // El 90 % y no el 100 %: un solo retraso en veinte planes no debería quitarla.
        if (vividos >= PLANES_PUNTUAL && aTiempo * 10 >= vividos * 9) {
            ganadas.add(Insignia.PUNTUAL.name());
        }
        if (propuestos >= PROPUESTOS_ORGANIZA) {
            ganadas.add(Insignia.ORGANIZA.name());
        }
        return ganadas;
    }

    private int propuestos(UUID usuarioId) {
        return (int) planes.countByCreadoPor_IdAndEstadoNot(usuarioId, Plan.Estado.CANCELADO);
    }

    private record Historial(Set<UUID> vividos, int aTiempo) {
    }

    private Historial historial(UUID usuarioId) {
        List<UUID> grupoIds = grupos.findByMiembro(usuarioId).stream().map(Grupo::getId).toList();
        if (grupoIds.isEmpty()) {
            return new Historial(Set.of(), 0);
        }
        String id = usuarioId.toString();
        Set<String> falto = ausencias.findByUsuarioId(id).stream().map(Ausencia::getPlanId).collect(Collectors.toSet());
        Set<String> tarde = retrasos.findByUsuarioId(id).stream().map(AlertaRetraso::getPlanId).collect(Collectors.toSet());
        Instant ahora = reloj.instant();

        List<Plan> confirmados = planes.findConfirmadosConFecha(grupoIds);
        Map<UUID, List<Llegada>> marcadas = confirmados.isEmpty() ? Map.of()
                : llegadas.deLosPlanes(confirmados.stream().map(Plan::getId).toList()).stream()
                        .collect(Collectors.groupingBy(l -> l.getId().getPlanId()));

        Set<UUID> vividos = new HashSet<>();
        int aTiempo = 0;
        for (Plan plan : confirmados) {
            VentanaPlan fecha = plan.getVentanaConfirmada();
            Instant inicio = LocalDateTime.of(fecha.getFecha(), fecha.getHoraInicio()).atZone(ZonaHoraria.ZONA).toInstant();
            if (inicio.isAfter(ahora)) {
                continue;
            }
            List<Llegada> delPlan = marcadas.get(plan.getId());
            if (delPlan != null) {
                // Alguien usó «Llegué» en este plan: cuenta lo marcado, no la suposición.
                Optional<Llegada> mia = delPlan.stream().filter(l -> l.getId().getUsuarioId().equals(usuarioId)).findFirst();
                if (mia.isPresent()) {
                    vividos.add(plan.getId());
                    if (LlegadaService.aTiempo(mia.get().getLlegoEn(), inicio)) {
                        aTiempo++;
                    }
                }
                continue;
            }
            String planId = plan.getId().toString();
            if (falto.contains(planId)) {
                continue;
            }
            vividos.add(plan.getId());
            if (!tarde.contains(planId)) {
                aTiempo++;
            }
        }
        return new Historial(vividos, aTiempo);
    }
}
