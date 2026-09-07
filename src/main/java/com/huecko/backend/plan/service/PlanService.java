package com.huecko.backend.plan.service;

import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.ForbiddenException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.grupo.dto.CeldaDisponibilidadResponse;
import com.huecko.backend.grupo.dto.DisponibilidadResponse;
import com.huecko.backend.grupo.service.GrupoService;
import com.huecko.backend.plan.dto.PlanRequests;
import com.huecko.backend.plan.dto.PlanResponse;
import com.huecko.backend.plan.dto.VentanaPlanResponse;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.entity.VentanaPlan;
import com.huecko.backend.postgres.entity.VotoVentana;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.postgres.repository.VotoVentanaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Módulo 3: propuestas de plan y votación (HU-08, HU-09, HU-10).
 *
 * Se apoya en el Módulo 2 para dos cosas: quién pertenece al grupo, y si una
 * ventana propuesta cumple de verdad el umbral de disponibilidad. Esa segunda
 * comprobación es el criterio de aceptación de HU-08 — "el sistema pre-filtra
 * solo ventanas que ya cumplen el umbral" — y no puede quedarse en el cliente:
 * el navegador pide las ventanas sugeridas y luego manda cuáles eligió, así
 * que sin validar aquí bastaría con editar la petición para proponer una hora
 * en la que medio grupo está en clase.
 */
@Service
@RequiredArgsConstructor
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    /** Un plazo más corto que esto no da tiempo a que nadie vote. */
    private static final int MINUTOS_MINIMOS_DE_PLAZO = 5;

    private final PlanRepository planRepository;
    private final VotoVentanaRepository votoVentanaRepository;
    private final MiembroGrupoRepository miembroGrupoRepository;
    private final UsuarioRepository usuarioRepository;
    private final GrupoService grupoService;
    private final SelectorVentanaGanadora selector;

    /* ------------------------------------------------------------------ *
     * Consulta
     * ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public List<PlanResponse> listar(UUID usuarioId, UUID grupoId) {
        exigirMiembro(usuarioId, grupoId);
        return planRepository.findByGrupoConVentanas(grupoId).stream()
                .map(plan -> aRespuesta(plan, usuarioId))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse detalle(UUID usuarioId, UUID planId) {
        Plan plan = planConAcceso(usuarioId, planId);
        return aRespuesta(plan, usuarioId);
    }

    /* ------------------------------------------------------------------ *
     * Proponer (RF-08)
     * ------------------------------------------------------------------ */

    @Transactional
    public PlanResponse crear(UUID usuarioId, UUID grupoId, PlanRequests.Crear req) {
        exigirMiembro(usuarioId, grupoId);
        Usuario creador = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NotFoundException("El usuario del token ya no existe"));

        Instant ahora = Instant.now();
        if (req.plazoVotacion().isBefore(ahora.plusSeconds(MINUTOS_MINIMOS_DE_PLAZO * 60L))) {
            throw new BusinessException(
                    "El plazo de votación debe dejar al menos " + MINUTOS_MINIMOS_DE_PLAZO
                            + " minutos para votar");
        }

        Grupo grupo = miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(grupoId, usuarioId)
                .orElseThrow(() -> new NotFoundException("El grupo no existe o no perteneces a él"))
                .getGrupo();

        Plan plan = Plan.builder()
                .grupo(grupo)
                .titulo(req.titulo().trim())
                .lugar(req.lugar() == null || req.lugar().isBlank() ? null : req.lugar().trim())
                .creadoPor(creador)
                .plazoVotacion(req.plazoVotacion())
                .estado(Plan.Estado.PROPUESTO)
                .votosMultiples(req.votosMultiples() == null || req.votosMultiples())
                .creadoEn(ahora)
                .build();

        plan.setVentanas(construirVentanas(usuarioId, grupoId, plan, req.ventanas()));

        return aRespuesta(planRepository.save(plan), usuarioId);
    }

    /**
     * Convierte las ventanas pedidas en entidades, rechazando las que no
     * cumplen el umbral del grupo (criterio de aceptación de HU-08).
     *
     * El cruce se pide una vez por semana distinta y no una por ventana: dos
     * opciones del mismo martes y jueves comparten el mismo cálculo.
     */
    private List<VentanaPlan> construirVentanas(UUID usuarioId, UUID grupoId, Plan plan,
                                                List<PlanRequests.Ventana> pedidas) {
        LocalDate hoy = LocalDate.now();
        Map<LocalDate, DisponibilidadResponse> cruces = new HashMap<>();
        Set<String> vistas = new HashSet<>();
        List<VentanaPlan> ventanas = new ArrayList<>(pedidas.size());

        for (PlanRequests.Ventana pedida : pedidas) {
            if (!pedida.horaFin().isAfter(pedida.horaInicio())) {
                throw new BusinessException("En cada ventana, la hora de fin debe ser posterior al inicio");
            }
            if (pedida.fecha().isBefore(hoy)) {
                throw new BusinessException("No se puede proponer una ventana en una fecha pasada");
            }
            if (!vistas.add(pedida.fecha() + "|" + pedida.horaInicio() + "|" + pedida.horaFin())) {
                throw new BusinessException("Hay dos ventanas idénticas: cada opción debe ser distinta");
            }

            DisponibilidadResponse cruce = cruces.computeIfAbsent(
                    lunesDe(pedida.fecha()),
                    semana -> grupoService.disponibilidad(usuarioId, grupoId, null, semana));

            ventanas.add(VentanaPlan.builder()
                    .plan(plan)
                    .fecha(pedida.fecha())
                    .horaInicio(pedida.horaInicio())
                    .horaFin(pedida.horaFin())
                    .disponibilidadPorcentaje(validarYMedir(cruce, pedida))
                    .build());
        }

        return ventanas;
    }

    /**
     * Comprueba que TODAS las horas de la ventana cumplan el umbral y devuelve
     * el porcentaje de la peor de ellas.
     *
     * Se exige en todas y no solo en la primera porque una ventana de 10 a 13
     * en la que a las 12 se cae media clase no es un hueco del grupo: es un
     * hueco que se acaba a mitad del plan.
     */
    private int validarYMedir(DisponibilidadResponse cruce, PlanRequests.Ventana ventana) {
        int dia = ventana.fecha().getDayOfWeek().getValue();
        int primeraHora = ventana.horaInicio().getHour();
        // Una ventana que acaba a las 12:00 ocupa hasta la franja de las 11;
        // una que acaba a las 12:30 sí llega a la de las 12.
        int ultimaHora = ventana.horaFin().getMinute() > 0
                ? ventana.horaFin().getHour()
                : ventana.horaFin().getHour() - 1;

        int minimo = 100;
        for (int hora = primeraHora; hora <= ultimaHora; hora++) {
            CeldaDisponibilidadResponse celda = buscarCelda(cruce, dia, hora);
            if (celda == null) {
                throw new BusinessException(
                        "La ventana del " + ventana.fecha() + " cae fuera del horario que analiza Huecko ("
                                + cruce.horaDesde() + ":00 a " + cruce.horaHasta() + ":00)");
            }
            if (!celda.cumpleUmbral()) {
                throw new BusinessException(
                        "La ventana del " + ventana.fecha() + " a las " + hora
                                + ":00 solo tiene un " + celda.porcentaje() + "% del grupo libre, "
                                + "por debajo del umbral de " + cruce.umbral() + "%");
            }
            minimo = Math.min(minimo, celda.porcentaje());
        }
        return minimo;
    }

    private CeldaDisponibilidadResponse buscarCelda(DisponibilidadResponse cruce, int dia, int hora) {
        return cruce.celdas().stream()
                .filter(c -> c.diaSemana() == dia && c.hora() == hora)
                .findFirst()
                .orElse(null);
    }

    private LocalDate lunesDe(LocalDate fecha) {
        return fecha.minusDays(fecha.getDayOfWeek().getValue() - 1L);
    }

    /* ------------------------------------------------------------------ *
     * Votar (RF-09)
     * ------------------------------------------------------------------ */

    @Transactional
    public PlanResponse votar(UUID usuarioId, UUID planId, UUID ventanaId) {
        Plan plan = planConAcceso(usuarioId, planId);
        exigirVotacionAbierta(plan);

        VentanaPlan ventana = plan.getVentanas().stream()
                .filter(v -> v.getId().equals(ventanaId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Esa ventana no pertenece a este plan"));

        // Con voto único, elegir otra opción sustituye a la anterior en vez de
        // fallar: para quien vota es "cambiar de idea", no un error.
        if (!plan.isVotosMultiples()) {
            votoVentanaRepository.deleteAll(
                    votoVentanaRepository.findByPlanIdAndUsuarioId(planId, usuarioId));
        }

        Usuario votante = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NotFoundException("El usuario del token ya no existe"));

        // El par (ventana, usuario) es la clave primaria, así que volver a
        // guardar el mismo voto lo sobrescribe en vez de duplicarlo.
        votoVentanaRepository.save(VotoVentana.builder()
                .ventana(ventana)
                .usuario(votante)
                .creadoEn(Instant.now())
                .build());

        return aRespuesta(plan, usuarioId);
    }

    @Transactional
    public PlanResponse quitarVoto(UUID usuarioId, UUID planId, UUID ventanaId) {
        Plan plan = planConAcceso(usuarioId, planId);
        exigirVotacionAbierta(plan);

        votoVentanaRepository.findByPlanIdAndUsuarioId(planId, usuarioId).stream()
                .filter(v -> v.getVentana().getId().equals(ventanaId))
                .forEach(votoVentanaRepository::delete);

        return aRespuesta(plan, usuarioId);
    }

    private void exigirVotacionAbierta(Plan plan) {
        if (plan.getEstado() != Plan.Estado.PROPUESTO) {
            throw new BusinessException("La votación de este plan ya está cerrada");
        }
        if (!plan.getPlazoVotacion().isAfter(Instant.now())) {
            throw new BusinessException("El plazo para votar este plan ya venció");
        }
    }

    /* ------------------------------------------------------------------ *
     * Cerrar (RF-10)
     * ------------------------------------------------------------------ */

    /**
     * Cierre a mano, antes de que venza el plazo. Solo el creador del plan o un
     * organizador del grupo: cualquiera podría, si no, cortar la votación en el
     * momento en que su opción va ganando.
     */
    @Transactional
    public PlanResponse cerrarManualmente(UUID usuarioId, UUID planId) {
        Plan plan = planConAcceso(usuarioId, planId);

        boolean esCreador = plan.getCreadoPor().getId().equals(usuarioId);
        boolean esOrganizador = miembroGrupoRepository
                .findByGrupo_IdAndUsuario_Id(plan.getGrupo().getId(), usuarioId)
                .map(m -> m.getRol() == MiembroGrupo.Rol.ORGANIZADOR)
                .orElse(false);

        if (!esCreador && !esOrganizador) {
            throw new ForbiddenException(
                    "Solo quien propuso el plan o un organizador del grupo puede cerrar la votación");
        }
        if (plan.getEstado() != Plan.Estado.PROPUESTO) {
            throw new BusinessException("Esta votación ya estaba cerrada");
        }

        cerrar(plan);
        return aRespuesta(planRepository.save(plan), usuarioId);
    }

    /**
     * Aplica el resultado de la votación. Lo usan tanto el cierre a mano como
     * el automático por plazo vencido, para que no puedan divergir.
     */
    public void cerrar(Plan plan) {
        List<VotoVentana> votos = votoVentanaRepository.findByPlanId(plan.getId());
        Optional<VentanaPlan> ganadora = selector.elegir(plan.getVentanas(), votos);

        plan.setCerradoEn(Instant.now());

        if (ganadora.isEmpty()) {
            plan.setEstado(Plan.Estado.CANCELADO);
            log.info("Plan {} cancelado: la votación cerró sin ningún voto", plan.getId());
            return;
        }

        plan.setEstado(Plan.Estado.CONFIRMADO);
        plan.setVentanaConfirmada(ganadora.get());
        log.info("Plan {} confirmado para el {} a las {}",
                plan.getId(), ganadora.get().getFecha(), ganadora.get().getHoraInicio());
    }

    /* ------------------------------------------------------------------ *
     * Apoyo
     * ------------------------------------------------------------------ */

    private MiembroGrupo exigirMiembro(UUID usuarioId, UUID grupoId) {
        return miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(grupoId, usuarioId)
                .orElseThrow(() -> new NotFoundException("El grupo no existe o no perteneces a él"));
    }

    /** Un plan de un grupo ajeno se responde como inexistente, igual que el grupo. */
    private Plan planConAcceso(UUID usuarioId, UUID planId) {
        Plan plan = planRepository.findByIdConVentanas(planId)
                .orElseThrow(() -> new NotFoundException("El plan no existe o no perteneces a su grupo"));

        if (!miembroGrupoRepository.existsByGrupo_IdAndUsuario_Id(plan.getGrupo().getId(), usuarioId)) {
            throw new NotFoundException("El plan no existe o no perteneces a su grupo");
        }
        return plan;
    }

    private PlanResponse aRespuesta(Plan plan, UUID usuarioId) {
        List<VotoVentana> votos = votoVentanaRepository.findByPlanId(plan.getId());
        Map<UUID, Integer> recuento = selector.recuentoPorVentana(votos);

        List<VentanaPlanResponse> ventanas = plan.getVentanas().stream()
                .map(ventana -> {
                    List<UUID> votantes = votos.stream()
                            .filter(v -> v.getVentana().getId().equals(ventana.getId()))
                            .map(v -> v.getUsuario().getId())
                            .toList();
                    return new VentanaPlanResponse(
                            ventana.getId(),
                            ventana.getFecha(),
                            ventana.diaSemana(),
                            ventana.getHoraInicio(),
                            ventana.getHoraFin(),
                            ventana.getDisponibilidadPorcentaje(),
                            recuento.getOrDefault(ventana.getId(), 0),
                            votantes,
                            votantes.contains(usuarioId));
                })
                .toList();

        return new PlanResponse(
                plan.getId(),
                plan.getGrupo().getId(),
                plan.getTitulo(),
                plan.getLugar(),
                plan.getCreadoPor().getId(),
                plan.getPlazoVotacion(),
                plan.getEstado(),
                plan.isVotosMultiples(),
                ventanas,
                plan.getVentanaConfirmada() == null ? null : plan.getVentanaConfirmada().getId(),
                plan.aceptaVotos(Instant.now()),
                plan.getCreadoEn(),
                plan.getCerradoEn());
    }

    /** Solo para que el cierre programado no tenga que conocer el repositorio. */
    @Transactional(readOnly = true)
    public List<Plan> vencidosSinCerrar(Instant limite) {
        return planRepository.findByEstadoAndPlazoVotacionLessThanEqual(Plan.Estado.PROPUESTO, limite);
    }

    /** Una transacción por plan: que uno falle no debe impedir cerrar el resto. */
    @Transactional
    public void cerrarPorPlazoVencido(UUID planId) {
        Plan plan = planRepository.findByIdConVentanas(planId).orElse(null);
        // Puede haberse cerrado a mano entre el barrido y esta llamada.
        if (plan == null || plan.getEstado() != Plan.Estado.PROPUESTO) {
            return;
        }
        cerrar(plan);
        planRepository.save(plan);
    }
}
