package com.huecko.backend.ia.service;

import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.ServicioNoDisponibleException;
import com.huecko.backend.grupo.service.GrupoService;
import com.huecko.backend.ia.ClienteIA;
import com.huecko.backend.ia.IaNoDisponibleException;
import com.huecko.backend.ia.dto.ResumenVotacionResponse;
import com.huecko.backend.imprevisto.service.ImprevistoService;
import com.huecko.backend.plan.dto.PlanResponse;
import com.huecko.backend.plan.dto.VentanaPlanResponse;
import com.huecko.backend.plan.service.PlanService;
import com.huecko.backend.retraso.service.RetrasoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * «Resumen con Huecko IA» de la votación de un plan, como en la app móvil.
 *
 * Lo puede pedir cualquier miembro y es de solo lectura: cuenta cómo va y
 * sugiere una acción a quien organiza, pero no cierra, ni cancela, ni
 * reprograma nada. La sugerencia solo puede ser una que el estado del plan
 * admite (cerrar o esperar mientras se vota; mantener o reprogramar una vez
 * confirmado); si la IA propone otra, se enseña el resumen sin sugerencia.
 */
@Service
public class ResumenVotacionService {

    private static final Logger log = LoggerFactory.getLogger(ResumenVotacionService.class);

    private static final Locale ES = Locale.forLanguageTag("es");
    private static final DateTimeFormatter CIERRE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, HH:mm", ES);
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM", ES);

    private final PlanService planService;
    private final GrupoService grupoService;
    private final ImprevistoService imprevistoService;
    private final RetrasoService retrasoService;
    private final ClienteIA clienteIA;
    private final AccesoAsistente acceso;
    private final Clock reloj;

    @Autowired
    public ResumenVotacionService(PlanService planService, GrupoService grupoService,
                                  ImprevistoService imprevistoService, RetrasoService retrasoService,
                                  ClienteIA clienteIA, AccesoAsistente acceso) {
        this(planService, grupoService, imprevistoService, retrasoService, clienteIA, acceso, Clock.systemUTC());
    }

    ResumenVotacionService(PlanService planService, GrupoService grupoService,
                           ImprevistoService imprevistoService, RetrasoService retrasoService,
                           ClienteIA clienteIA, AccesoAsistente acceso, Clock reloj) {
        this.planService = planService;
        this.grupoService = grupoService;
        this.imprevistoService = imprevistoService;
        this.retrasoService = retrasoService;
        this.clienteIA = clienteIA;
        this.acceso = acceso;
        this.reloj = reloj;
    }

    public ResumenVotacionResponse resumir(UUID usuarioId, UUID planId) {
        PlanResponse plan = planService.detalle(usuarioId, planId);
        List<String> opciones = opcionesPara(plan);
        if (opciones.isEmpty()) {
            throw new BusinessException("Este plan está cancelado: no hay votación que resumir.");
        }
        int integrantes = grupoService.detalle(usuarioId, plan.grupoId()).miembros().size();
        ClienteIA.PeticionResumen peticion = new ClienteIA.PeticionResumen(
                plan.titulo(), plan.lugar(), estadoParaIA(plan), plan.votacionAbierta(),
                legible(plan.plazoVotacion()), HuecosDelGrupo.ahoraLegible(reloj.instant()),
                integrantes, votantes(plan),
                plan.ventanas().stream().map(v -> aVentanaIA(v, plan)).toList(),
                imprevistoService.ausencias(usuarioId, planId).stream()
                        .map(a -> new ClienteIA.AusenciaIA(a.nombreUsuario(), a.critica(), a.motivo()))
                        .toList(),
                retrasoService.listar(usuarioId, planId).stream()
                        .map(r -> new ClienteIA.RetrasoIA(r.nombreUsuario(), r.minutosEstimados()))
                        .toList(),
                imprevistoService.abierta(usuarioId, planId)
                        .map(v -> new ClienteIA.ExpresIA(v.nombreReporta(), v.votosEmitidos(), v.miembrosDelGrupo()))
                        .orElse(null),
                opciones);

        acceso.exigir(usuarioId);
        ClienteIA.RespuestaResumen respuesta;
        try {
            respuesta = clienteIA.resumenVotacion(peticion);
        } catch (IaNoDisponibleException ex) {
            log.warn("Resumen de la votación del plan {} sin IA: {}", planId, ex.getMessage());
            throw new ServicioNoDisponibleException(AccesoAsistente.NO_DISPONIBLE);
        }
        if (respuesta.resumen() == null || respuesta.resumen().isBlank()) {
            throw new ServicioNoDisponibleException(AccesoAsistente.NO_DISPONIBLE);
        }

        boolean admisible = opciones.contains(respuesta.recomendacion()) && respuesta.razon() != null;
        return new ResumenVotacionResponse(respuesta.resumen().strip(),
                admisible ? respuesta.recomendacion() : null,
                admisible ? respuesta.razon().strip() : null);
    }

    /** Lo que quien organiza puede hacer de verdad con el plan en su estado actual. */
    static List<String> opcionesPara(PlanResponse plan) {
        return switch (plan.estado()) {
            case PROPUESTO -> plan.votacionAbierta() ? List.of("CERRAR", "ESPERAR") : List.of("CERRAR");
            case CONFIRMADO -> List.of("MANTENER", "REPROGRAMAR");
            case EN_RECOORDINACION -> List.of("REPROGRAMAR");
            case CANCELADO -> List.of();
        };
    }

    private static String estadoParaIA(PlanResponse plan) {
        return switch (plan.estado()) {
            case PROPUESTO -> "EN_VOTACION";
            case EN_RECOORDINACION -> "POR_REPROGRAMAR";
            default -> plan.estado().name();
        };
    }

    /** Personas distintas que votaron: con voto múltiple, una misma puede apoyar varias opciones. */
    static int votantes(PlanResponse plan) {
        return (int) plan.ventanas().stream()
                .flatMap(v -> v.votantes() == null ? Stream.empty() : v.votantes().stream())
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    private static ClienteIA.VentanaVotadaIA aVentanaIA(VentanaPlanResponse v, PlanResponse plan) {
        String opcion = v.fecha().getDayOfWeek().getDisplayName(TextStyle.FULL, ES) + " " + v.fecha().format(DIA)
                + " " + v.horaInicio() + "-" + v.horaFin();
        return new ClienteIA.VentanaVotadaIA(opcion, v.votos(), v.disponibilidadPorcentaje(),
                v.id().equals(plan.ventanaConfirmadaId()));
    }

    private static String legible(Instant instante) {
        return instante == null ? "sin plazo" : LocalDateTime.ofInstant(instante, ZonaHoraria.ZONA).format(CIERRE);
    }
}
