package com.huecko.backend.imprevisto.service;

import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.imprevisto.dto.ImprevistoDtos;
import com.huecko.backend.mongo.document.VotacionExpres;
import com.huecko.backend.mongo.repository.VotacionExpresRepository;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.tiemporeal.NotificadorTiempoReal;
import com.huecko.backend.tiemporeal.dto.EventoTiempoReal;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Módulo 5: imprevistos de último minuto (HU-13 a HU-16).
 *
 * El recorrido es el de CU-05: alguien avisa de que no puede ir, unas reglas
 * deciden si eso es grave, y según la respuesta el plan se replantea o
 * simplemente se informa.
 */
@Service
@RequiredArgsConstructor
public class ImprevistoService {

    private static final Logger log = LoggerFactory.getLogger(ImprevistoService.class);

    private final VotacionExpresRepository votacionRepository;
    private final PlanRepository planRepository;
    private final MiembroGrupoRepository miembroGrupoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EvaluadorCriticidad evaluador;
    private final NotificadorTiempoReal notificador;

    /** RF-17: «plazo corto». Una hora es el ejemplo del documento. */
    @Value("${huecko.imprevistos.plazo-minutos:60}")
    private int plazoMinutos;

    /** RF-18: qué se aplica si nadie vota a tiempo. */
    @Value("${huecko.imprevistos.resultado-por-defecto:MANTENER}")
    private VotacionExpres.Opcion resultadoPorDefecto;

    /** Días que se conserva una votación cerrada antes de que el TTL la borre. */
    @Value("${huecko.imprevistos.dias-purga:7}")
    private int diasPurga;

    /* ------------------------------------------------------------------ *
     * Reportar (RF-15, RF-16, RF-17, RF-19)
     * ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public ImprevistoDtos.ResultadoReporte reportar(UUID usuarioId, UUID planId, String motivo) {
        Plan plan = planConAcceso(usuarioId, planId);

        if (plan.getEstado() != Plan.Estado.CONFIRMADO) {
            throw new BusinessException(
                    "Solo se puede reportar un imprevisto en un plan confirmado");
        }

        if (votacionRepository.findByPlanIdAndEstado(
                planId.toString(), VotacionExpres.Estado.ABIERTA).isPresent()) {
            // Dos votaciones exprés a la vez sobre el mismo plan dejarían al
            // grupo decidiendo dos cosas incompatibles en paralelo.
            throw new BusinessException(
                    "Ya hay una votación exprés abierta para este plan");
        }

        MiembroGrupo miembro = miembroGrupoRepository
                .findByGrupo_IdAndUsuario_Id(plan.getGrupo().getId(), usuarioId)
                .orElseThrow(() -> new NotFoundException("El plan no existe"));
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NotFoundException("El usuario del token ya no existe"));

        EvaluadorCriticidad.Veredicto veredicto = evaluador.evaluar(plan, miembro, usuarioId);
        String motivoLimpio = (motivo == null || motivo.isBlank()) ? null : motivo.trim();

        // RF-19: baja no crítica. Se informa y el plan sigue su curso.
        if (!veredicto.esCritica()) {
            notificador.aGrupo(plan.getGrupo().getId(),
                    EventoTiempoReal.Tipo.AUSENCIA_REPORTADA,
                    datosDeAusencia(plan, usuario, motivoLimpio));
            return new ImprevistoDtos.ResultadoReporte(
                    veredicto.criticidad(), veredicto.razon(), null);
        }

        // RF-17: baja crítica. Se abre la votación exprés.
        Instant ahora = Instant.now();
        VotacionExpres votacion = votacionRepository.save(VotacionExpres.builder()
                .planId(planId.toString())
                .grupoId(plan.getGrupo().getId().toString())
                .usuarioReporta(usuarioId.toString())
                .nombreReporta(usuario.getNombre())
                .motivo(motivoLimpio)
                .criticidad(veredicto.criticidad())
                .razonCriticidad(veredicto.razon())
                .estado(VotacionExpres.Estado.ABIERTA)
                .votos(new LinkedHashMap<>())
                .miembrosDelGrupo((int) miembroGrupoRepository.countByGrupo_Id(plan.getGrupo().getId()))
                .abiertaEn(ahora)
                .expiraEn(ahora.plus(Duration.ofMinutes(plazoMinutos)))
                .build());

        notificador.aGrupo(plan.getGrupo().getId(),
                EventoTiempoReal.Tipo.VOTACION_EXPRES_ABIERTA,
                datosDeVotacion(plan, votacion));

        return new ImprevistoDtos.ResultadoReporte(
                veredicto.criticidad(), veredicto.razon(),
                ImprevistoDtos.VotacionExpresResponse.from(votacion, usuarioId.toString()));
    }

    /* ------------------------------------------------------------------ *
     * Votar (RF-17)
     * ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public ImprevistoDtos.VotacionExpresResponse votar(UUID usuarioId, UUID planId,
                                                       VotacionExpres.Opcion opcion) {
        planConAcceso(usuarioId, planId);

        VotacionExpres votacion = votacionRepository
                .findByPlanIdAndEstado(planId.toString(), VotacionExpres.Estado.ABIERTA)
                .orElseThrow(() -> new NotFoundException(
                        "No hay ninguna votación exprés abierta para este plan"));

        if (Instant.now().isAfter(votacion.getExpiraEn())) {
            // El barrido aún no ha pasado, pero el plazo ya venció: aceptar el
            // voto lo haría contar en una votación que debía estar cerrada.
            throw new BusinessException("El plazo de la votación exprés ya venció");
        }

        // Cambiar de opinión sustituye el voto anterior, no suma otro.
        votacion.getVotos().put(usuarioId.toString(), opcion);
        VotacionExpres guardada = votacionRepository.save(votacion);

        return ImprevistoDtos.VotacionExpresResponse.from(guardada, usuarioId.toString());
    }

    @Transactional(readOnly = true)
    public Optional<ImprevistoDtos.VotacionExpresResponse> abierta(UUID usuarioId, UUID planId) {
        planConAcceso(usuarioId, planId);
        return votacionRepository
                .findByPlanIdAndEstado(planId.toString(), VotacionExpres.Estado.ABIERTA)
                .map(v -> ImprevistoDtos.VotacionExpresResponse.from(v, usuarioId.toString()));
    }

    /* ------------------------------------------------------------------ *
     * Cerrar (RF-17, RF-18)
     * ------------------------------------------------------------------ */

    public List<VotacionExpres> vencidasSinCerrar(Instant limite) {
        return votacionRepository.findByEstadoAndExpiraEnLessThanEqual(
                VotacionExpres.Estado.ABIERTA, limite);
    }

    /**
     * Aplica el resultado de una votación exprés.
     *
     * RF-18: sin ningún voto se aplica el resultado por defecto. «Sin quórum»
     * se interpreta como «nadie votó», no como «votó menos de la mitad»: con
     * un plazo de una hora, exigir mayoría del grupo cancelaría planes por
     * simple falta de atención, que es justo lo contrario de lo que busca el
     * módulo.
     */
    @Transactional
    public void cerrar(String votacionId) {
        VotacionExpres votacion = votacionRepository.findById(votacionId).orElse(null);
        if (votacion == null || votacion.getEstado() != VotacionExpres.Estado.ABIERTA) {
            return; // se cerró entre el barrido y esta llamada
        }

        boolean sinVotos = votacion.getVotos().isEmpty();
        VotacionExpres.Opcion resultado = sinVotos
                ? resultadoPorDefecto
                : masVotada(votacion.getVotos());

        Instant ahora = Instant.now();
        votacion.setEstado(VotacionExpres.Estado.CERRADA);
        votacion.setCerradaEn(ahora);
        votacion.setResultado(resultado);
        votacion.setResultadoPorDefecto(sinVotos);
        // Solo ahora se marca para purga: mientras estaba abierta, el índice
        // TTL la ignoraba porque este campo era nulo.
        votacion.setPurgarEn(ahora.plus(Duration.ofDays(diasPurga)));
        votacionRepository.save(votacion);

        aplicarAlPlan(votacion, resultado);

        log.info("Votación exprés {} cerrada con {}{}", votacionId, resultado,
                sinVotos ? " (por defecto, nadie votó)" : "");
    }

    /**
     * Desempate: gana la opción más conservadora de las empatadas, en el orden
     * MANTENER, REAGENDAR, CANCELAR. Ante la duda el plan sigue en pie, porque
     * deshacer una cancelación cuesta más que reagendar después.
     *
     * <b>Ojo: el Módulo 3 desempata con otro criterio.</b> Allí
     * ({@code SelectorVentanaGanadora}) gana la ventana más próxima, porque lo
     * que se empata son horas equivalentes entre sí y la cercanía es lo que
     * menos sorprende. Aquí las opciones tienen consecuencias de distinto peso,
     * así que el criterio es el daño reversible. No son incoherentes:
     * desempatan cosas distintas.
     */
    private VotacionExpres.Opcion masVotada(Map<String, VotacionExpres.Opcion> votos) {
        Map<VotacionExpres.Opcion, Integer> recuento = new EnumMap<>(VotacionExpres.Opcion.class);
        votos.values().forEach(op -> recuento.merge(op, 1, Integer::sum));

        return recuento.entrySet().stream()
                .max(Comparator
                        .comparingInt(Map.Entry<VotacionExpres.Opcion, Integer>::getValue)
                        .thenComparing(e -> prioridadDeDesempate(e.getKey()),
                                Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .orElse(resultadoPorDefecto);
    }

    private int prioridadDeDesempate(VotacionExpres.Opcion opcion) {
        return switch (opcion) {
            case MANTENER -> 0;
            case REAGENDAR -> 1;
            case CANCELAR -> 2;
        };
    }

    private void aplicarAlPlan(VotacionExpres votacion, VotacionExpres.Opcion resultado) {
        Plan plan = planRepository.findById(UUID.fromString(votacion.getPlanId())).orElse(null);
        if (plan == null) {
            return;
        }

        switch (resultado) {
            case CANCELAR -> plan.setEstado(Plan.Estado.CANCELADO);
            // El plan vuelve a coordinación: la fecha confirmada ya no vale,
            // pero el plan no se tira. EN_RECOORDINACION ya existía en el enum.
            case REAGENDAR -> plan.setEstado(Plan.Estado.EN_RECOORDINACION);
            // MANTENER no toca nada: sigue CONFIRMADO con su fecha.
            case MANTENER -> { }
        }
        planRepository.save(plan);

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("planId", plan.getId().toString());
        datos.put("tituloPlan", plan.getTitulo());
        datos.put("resultado", resultado.name());
        datos.put("porDefecto", votacion.isResultadoPorDefecto());
        datos.put("estadoPlan", plan.getEstado().name());

        notificador.aGrupo(plan.getGrupo().getId(),
                EventoTiempoReal.Tipo.VOTACION_EXPRES_CERRADA, datos);
    }

    /* ------------------------------------------------------------------ */

    private Map<String, Object> datosDeAusencia(Plan plan, Usuario usuario, String motivo) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("planId", plan.getId().toString());
        datos.put("tituloPlan", plan.getTitulo());
        datos.put("usuarioId", usuario.getId().toString());
        datos.put("nombreUsuario", usuario.getNombre());
        if (motivo != null) {
            datos.put("motivo", motivo);
        }
        return datos;
    }

    private Map<String, Object> datosDeVotacion(Plan plan, VotacionExpres votacion) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("planId", plan.getId().toString());
        datos.put("tituloPlan", plan.getTitulo());
        datos.put("votacionId", votacion.getId());
        datos.put("nombreReporta", votacion.getNombreReporta());
        datos.put("razonCriticidad", votacion.getRazonCriticidad());
        datos.put("expiraEn", votacion.getExpiraEn().toString());
        if (votacion.getMotivo() != null) {
            datos.put("motivo", votacion.getMotivo());
        }
        return datos;
    }

    private Plan planConAcceso(UUID usuarioId, UUID planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException("El plan no existe"));

        if (!miembroGrupoRepository.existsByGrupo_IdAndUsuario_Id(
                plan.getGrupo().getId(), usuarioId)) {
            throw new NotFoundException("El plan no existe");
        }
        return plan;
    }
}
