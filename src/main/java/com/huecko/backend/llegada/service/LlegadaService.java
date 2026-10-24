package com.huecko.backend.llegada.service;

import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.llegada.dto.LlegadasResponse;
import com.huecko.backend.postgres.entity.Llegada;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.entity.VentanaPlan;
import com.huecko.backend.postgres.repository.LlegadaRepository;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.tiemporeal.LlegadaRegistrada;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * «Llegué»: cada quien marca que llegó a un plan confirmado. Se puede desde
 * media hora antes de que empiece hasta una hora después de que termine, y
 * cuenta como a tiempo si fue hasta diez minutos tarde. Con esto los logros
 * dejan de suponer la asistencia (ver PerfilPublicoService).
 */
@Service
public class LlegadaService {

    public static final Duration ABRE_ANTES = Duration.ofMinutes(30);
    public static final Duration CIERRA_DESPUES = Duration.ofHours(1);
    public static final Duration TOLERANCIA = Duration.ofMinutes(10);
    private static final String NO_EXISTE = "El plan no existe o no perteneces a su grupo";

    private final PlanRepository planes;
    private final LlegadaRepository llegadas;
    private final MiembroGrupoRepository miembros;
    private final UsuarioRepository usuarios;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    @Autowired
    public LlegadaService(PlanRepository planes, LlegadaRepository llegadas, MiembroGrupoRepository miembros,
                          UsuarioRepository usuarios, ApplicationEventPublisher eventos) {
        this(planes, llegadas, miembros, usuarios, eventos, Clock.systemUTC());
    }

    LlegadaService(PlanRepository planes, LlegadaRepository llegadas, MiembroGrupoRepository miembros,
                   UsuarioRepository usuarios, ApplicationEventPublisher eventos, Clock reloj) {
        this.planes = planes;
        this.llegadas = llegadas;
        this.miembros = miembros;
        this.usuarios = usuarios;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public LlegadasResponse ver(UUID usuarioId, UUID planId) {
        return respuesta(planConAcceso(usuarioId, planId), usuarioId);
    }

    /** Marcar dos veces no cambia la hora de la primera. */
    @Transactional
    public LlegadasResponse marcar(UUID usuarioId, UUID planId) {
        Plan plan = planConAcceso(usuarioId, planId);
        Instant ahora = reloj.instant().truncatedTo(ChronoUnit.MICROS);
        if (estado(plan, ahora) != LlegadasResponse.Estado.ABIERTA) {
            throw new BusinessException("«Llegué» se marca desde media hora antes del plan hasta una hora después de que termine.");
        }
        Llegada.Clave clave = new Llegada.Clave(planId, usuarioId);
        if (!llegadas.existsById(clave)) {
            llegadas.save(new Llegada(clave, ahora));
            String nombre = usuarios.findById(usuarioId).map(Usuario::getNombre).orElse("Alguien");
            eventos.publishEvent(new LlegadaRegistrada(plan.getGrupo().getId(), planId, plan.getTitulo(), usuarioId,
                    nombre, aTiempo(ahora, inicio(plan))));
        }
        return respuesta(plan, usuarioId);
    }

    /** Por si se marcó sin querer. Solo mientras se puede marcar. */
    @Transactional
    public LlegadasResponse quitar(UUID usuarioId, UUID planId) {
        Plan plan = planConAcceso(usuarioId, planId);
        if (estado(plan, reloj.instant()) != LlegadasResponse.Estado.ABIERTA) {
            throw new BusinessException("Ya no se puede cambiar: el plan terminó hace más de una hora.");
        }
        llegadas.deleteById(new Llegada.Clave(planId, usuarioId));
        return respuesta(plan, usuarioId);
    }

    public static boolean aTiempo(Instant llegoEn, Instant inicio) {
        return inicio != null && !llegoEn.isAfter(inicio.plus(TOLERANCIA));
    }

    public static Instant inicio(Plan plan) {
        VentanaPlan v = plan.getVentanaConfirmada();
        return v == null ? null : LocalDateTime.of(v.getFecha(), v.getHoraInicio()).atZone(ZonaHoraria.ZONA).toInstant();
    }

    private static Instant fin(Plan plan) {
        VentanaPlan v = plan.getVentanaConfirmada();
        if (v == null) {
            return null;
        }
        LocalDateTime fin = LocalDateTime.of(v.getFecha(), v.getHoraFin());
        // Un plan que termina a medianoche (o «antes» de empezar) acaba al día siguiente.
        if (!v.getHoraFin().isAfter(v.getHoraInicio())) {
            fin = fin.plusDays(1);
        }
        return fin.atZone(ZonaHoraria.ZONA).toInstant();
    }

    static LlegadasResponse.Estado estado(Plan plan, Instant ahora) {
        Instant inicio = inicio(plan);
        if (plan.getEstado() != Plan.Estado.CONFIRMADO || inicio == null) {
            return LlegadasResponse.Estado.SIN_FECHA;
        }
        if (ahora.isBefore(inicio.minus(ABRE_ANTES))) {
            return LlegadasResponse.Estado.ANTES;
        }
        return ahora.isAfter(fin(plan).plus(CIERRA_DESPUES))
                ? LlegadasResponse.Estado.CERRADA
                : LlegadasResponse.Estado.ABIERTA;
    }

    private LlegadasResponse respuesta(Plan plan, UUID usuarioId) {
        List<Llegada> lista = llegadas.delPlan(plan.getId());
        Map<UUID, Usuario> personas = usuarios.findAllById(lista.stream().map(l -> l.getId().getUsuarioId()).toList())
                .stream().collect(Collectors.toMap(Usuario::getId, Function.identity()));
        Instant inicio = inicio(plan);
        List<LlegadasResponse.Persona> llegaron = lista.stream().map(l -> {
            Usuario u = personas.get(l.getId().getUsuarioId());
            return new LlegadasResponse.Persona(l.getId().getUsuarioId(), u == null ? "Alguien" : u.getNombre(),
                    u == null ? null : u.getFotoActualizadaEn(), l.getLlegoEn(), aTiempo(l.getLlegoEn(), inicio));
        }).toList();
        Instant fin = fin(plan);
        return new LlegadasResponse(estado(plan, reloj.instant()),
                inicio == null ? null : inicio.minus(ABRE_ANTES), fin == null ? null : fin.plus(CIERRA_DESPUES),
                llegaron.stream().anyMatch(p -> p.usuarioId().equals(usuarioId)), llegaron);
    }

    private Plan planConAcceso(UUID usuarioId, UUID planId) {
        Plan plan = planes.findByIdConVentanas(planId).orElseThrow(() -> new NotFoundException(NO_EXISTE));
        if (!miembros.existsByGrupo_IdAndUsuario_Id(plan.getGrupo().getId(), usuarioId)) {
            throw new NotFoundException(NO_EXISTE);
        }
        return plan;
    }
}
