package com.huecko.backend.ia.service;

import com.huecko.backend.common.exception.ServicioNoDisponibleException;
import com.huecko.backend.grupo.dto.GrupoResponse;
import com.huecko.backend.grupo.service.GrupoService;
import com.huecko.backend.ia.ClienteIA;
import com.huecko.backend.ia.IaNoDisponibleException;
import com.huecko.backend.ia.dto.IdeasPlanResponse;
import com.huecko.backend.mongo.document.LlamadaIA;
import com.huecko.backend.plan.dto.PlanResponse;
import com.huecko.backend.plan.service.PlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * «Ideas con IA», como en la app móvil: tres planes pensados para los huecos
 * libres del grupo, sin repetir los que ya propuso.
 *
 * Igual que en el borrador, la IA elige el hueco por número y aquí se
 * comprueba; una idea con un número inventado se queda sin horario en vez de
 * proponer un momento en el que alguien está ocupado.
 */
@Service
public class IdeasPlanService {

    private static final Logger log = LoggerFactory.getLogger(IdeasPlanService.class);

    /** Bastan los últimos para no repetir; más solo alarga el mensaje. */
    static final int PLANES_RECIENTES = 5;
    private static final Set<String> CATEGORIAS = Set.of("ESTUDIO", "REUNION", "COMIDA", "DEPORTE", "SALIDA", "OTRO");

    private final GrupoService grupoService;
    private final PlanService planService;
    private final HuecosDelGrupo huecosDelGrupo;
    private final ClienteIA clienteIA;
    private final AccesoAsistente acceso;
    private final RegistroUsoIA registro;
    private final Clock reloj;

    @Autowired
    public IdeasPlanService(GrupoService grupoService, PlanService planService, HuecosDelGrupo huecosDelGrupo,
                            ClienteIA clienteIA, AccesoAsistente acceso, RegistroUsoIA registro) {
        this(grupoService, planService, huecosDelGrupo, clienteIA, acceso, registro, Clock.systemUTC());
    }

    IdeasPlanService(GrupoService grupoService, PlanService planService, HuecosDelGrupo huecosDelGrupo,
                     ClienteIA clienteIA, AccesoAsistente acceso, RegistroUsoIA registro, Clock reloj) {
        this.grupoService = grupoService;
        this.planService = planService;
        this.huecosDelGrupo = huecosDelGrupo;
        this.clienteIA = clienteIA;
        this.acceso = acceso;
        this.registro = registro;
        this.reloj = reloj;
    }

    public IdeasPlanResponse proponer(UUID usuarioId, UUID grupoId) {
        Instant ahora = reloj.instant();
        GrupoResponse grupo = grupoService.detalle(usuarioId, grupoId);
        List<String> recientes = planService.listar(usuarioId, grupoId).stream()
                .sorted(Comparator.comparing(PlanResponse::creadoEn, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(PLANES_RECIENTES)
                .map(PlanResponse::titulo)
                .toList();
        List<HuecosDelGrupo.Hueco> huecos = huecosDelGrupo.calcular(usuarioId, grupoId, ahora);

        ClienteIA.PeticionIdeas peticion = new ClienteIA.PeticionIdeas(
                grupo.nombre(), grupo.descripcion() == null ? "" : grupo.descripcion(),
                grupo.miembros().size(), HuecosDelGrupo.ahoraLegible(ahora), recientes,
                huecos.stream().map(HuecosDelGrupo::paraIA).toList());

        acceso.exigir(usuarioId);
        ClienteIA.RespuestaIdeas respuesta;
        try {
            respuesta = registro.medir(LlamadaIA.Funcion.IDEAS_PLAN, () -> clienteIA.ideasPlan(peticion));
        } catch (IaNoDisponibleException ex) {
            log.warn("Ideas de plan sin IA en el grupo {}: {}", grupoId, ex.getMessage());
            throw new ServicioNoDisponibleException(AccesoAsistente.NO_DISPONIBLE);
        }

        Map<Integer, HuecosDelGrupo.Hueco> porNumero = huecos.stream()
                .collect(Collectors.toMap(HuecosDelGrupo.Hueco::numero, Function.identity()));
        List<IdeasPlanResponse.Idea> ideas = respuesta.ideas() == null ? List.of() : respuesta.ideas().stream()
                .filter(Objects::nonNull)
                .filter(i -> i.titulo() != null && !i.titulo().isBlank() && i.razon() != null)
                .limit(3)
                .map(i -> aIdea(i, porNumero))
                .toList();
        // El servicio ya exige al menos una; sin ninguna no hay nada que enseñar.
        if (ideas.isEmpty()) {
            throw new ServicioNoDisponibleException(AccesoAsistente.NO_DISPONIBLE);
        }
        return new IdeasPlanResponse(ideas);
    }

    private static IdeasPlanResponse.Idea aIdea(ClienteIA.IdeaIA idea, Map<Integer, HuecosDelGrupo.Hueco> huecos) {
        HuecosDelGrupo.Hueco hueco = idea.numero() == null ? null : huecos.get(idea.numero());
        return new IdeasPlanResponse.Idea(
                idea.titulo().strip(),
                CATEGORIAS.contains(idea.categoria()) ? idea.categoria() : "OTRO",
                idea.lugar() == null || idea.lugar().isBlank() ? null : idea.lugar().strip(),
                // Las primeras dos horas del hueco: una idea no dice cuánto dura.
                hueco == null ? null : BorradorPlanService.tramo(hueco, null, null),
                idea.razon().strip());
    }
}
