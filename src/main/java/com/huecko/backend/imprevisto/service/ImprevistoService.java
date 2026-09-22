package com.huecko.backend.imprevisto.service;

import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.imprevisto.dto.ImprevistoDtos;
import com.huecko.backend.mongo.document.Ausencia;
import com.huecko.backend.mongo.document.VotacionExpres;
import com.huecko.backend.mongo.repository.AlertaRetrasoRepository;
import com.huecko.backend.mongo.repository.AusenciaRepository;
import com.huecko.backend.mongo.repository.VotacionExpresOperaciones;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
    private final VotacionExpresOperaciones operaciones;
    private final PlanRepository planRepository;
    private final MiembroGrupoRepository miembroGrupoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EvaluadorCriticidad evaluador;
    private final NotificadorTiempoReal notificador;
    private final AusenciaRepository ausenciaRepository;
    private final AlertaRetrasoRepository alertaRetrasoRepository;

    /** Nadie vota en menos de esto, por cerca que esté el plan. */
    private static final Duration PLAZO_MINIMO = Duration.ofMinutes(5);

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
        if (plan.yaTermino(Instant.now())) {
            throw new BusinessException("Este plan ya terminó: no se pueden reportar imprevistos");
        }

        // Antes de nada: un segundo aviso de la misma persona no es un hecho
        // nuevo, y cada uno volvía a notificar al grupo entero. Vale para las
        // dos ramas; tras reagendar, las ausencias viejas se borran y se puede
        // volver a avisar (ver PlanService.reagendar).
        if (ausenciaRepository.existsByPlanIdAndUsuarioId(planId.toString(), usuarioId.toString())) {
            throw new BusinessException("Ya reportaste tu ausencia en este plan");
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
        log.debug("Criticidad de la ausencia en el plan {}: {} ({}) por {}",
                planId, veredicto.criticidad(), veredicto.razon(), veredicto.origen());
        String motivoLimpio = (motivo == null || motivo.isBlank()) ? null : motivo.trim();

        Instant ahora = Instant.now();
        // Se guarda primero la ausencia: su índice único es lo que frena dos
        // avisos simultáneos de la misma persona, y así ninguno de los dos
        // llega a abrir votación ni a notificar.
        Ausencia ausencia = registrarAusencia(plan, usuario, motivoLimpio, veredicto.esCritica(), ahora);

        // RF-19: baja no crítica. Se informa y el plan sigue su curso.
        if (!veredicto.esCritica()) {
            retirarRetraso(plan, usuarioId);
            notificador.aGrupo(plan.getGrupo().getId(),
                    EventoTiempoReal.Tipo.AUSENCIA_REPORTADA,
                    datosDeAusencia(plan, ausencia));
            return new ImprevistoDtos.ResultadoReporte(
                    veredicto.criticidad(), veredicto.razon(), veredicto.origen().name(), null);
        }

        // RF-17: baja crítica. Se abre la votación exprés.
        VotacionExpres votacion;
        try {
            votacion = votacionRepository.save(VotacionExpres.builder()
                    .planId(planId.toString())
                    .grupoId(plan.getGrupo().getId().toString())
                    .usuarioReporta(usuarioId.toString())
                    .nombreReporta(usuario.getNombre())
                    .motivo(motivoLimpio)
                    .criticidad(veredicto.criticidad())
                    .razonCriticidad(veredicto.razon())
                    .origenCriticidad(veredicto.origen().name())
                    .estado(VotacionExpres.Estado.ABIERTA)
                    .votos(new LinkedHashMap<>())
                    .miembrosDelGrupo((int) miembroGrupoRepository.countByGrupo_Id(plan.getGrupo().getId()))
                    .abiertaEn(ahora)
                    .expiraEn(calcularExpiracion(plan, ahora))
                    .build());
        } catch (RuntimeException ex) {
            // Sin votación, la ausencia crítica no se ha podido tramitar: se
            // deshace, o esa persona quedaría marcada y sin poder reintentar.
            ausenciaRepository.deleteByPlanIdAndUsuarioId(planId.toString(), usuarioId.toString());
            if (ex instanceof DuplicateKeyException) {
                // Otro aviso abrió la votación en el mismo instante (índice
                // único `uniq_abierta_por_plan`).
                throw new BusinessException("Ya hay una votación exprés abierta para este plan");
            }
            throw ex;
        }

        retirarRetraso(plan, usuarioId);
        notificador.aGrupo(plan.getGrupo().getId(),
                EventoTiempoReal.Tipo.VOTACION_EXPRES_ABIERTA,
                datosDeVotacion(plan, votacion));

        return new ImprevistoDtos.ResultadoReporte(
                veredicto.criticidad(), veredicto.razon(), veredicto.origen().name(),
                aRespuesta(votacion, usuarioId));
    }

    private Ausencia registrarAusencia(Plan plan, Usuario usuario, String motivo, boolean critica, Instant ahora) {
        try {
            return ausenciaRepository.save(Ausencia.builder()
                    .planId(plan.getId().toString())
                    .grupoId(plan.getGrupo().getId().toString())
                    .usuarioId(usuario.getId().toString())
                    .nombreUsuario(usuario.getNombre())
                    .motivo(motivo)
                    .critica(critica)
                    .reportadoEn(ahora)
                    .build());
        } catch (DuplicateKeyException ex) {
            // El mismo aviso enviado dos veces a la vez (doble clic, reintento
            // de red): la comprobación previa no lo frena, el índice sí.
            throw new BusinessException("Ya reportaste tu ausencia en este plan");
        }
    }

    /**
     * Quien no viene deja de llegar tarde. Sin esto, la fila de puntualidad
     * seguía diciendo «Ana llega 10 minutos tarde» junto a «Ana no viene».
     * Se avisa con el mismo evento que un retiro a mano para que el cliente
     * no necesite una regla nueva.
     */
    private void retirarRetraso(Plan plan, UUID usuarioId) {
        long borradas = alertaRetrasoRepository.deleteByPlanIdAndUsuarioId(
                plan.getId().toString(), usuarioId.toString());
        if (borradas > 0) {
            notificador.aGrupo(plan.getGrupo().getId(),
                    EventoTiempoReal.Tipo.RETRASO_REPORTADO,
                    Map.of("planId", plan.getId().toString(),
                            "usuarioId", usuarioId.toString(),
                            "retirado", true));
        }
    }

    /** RF-19: quién no viene, críticas y no críticas, para pintarlo al recargar. */
    @Transactional(readOnly = true)
    public List<ImprevistoDtos.AusenciaResponse> ausencias(UUID usuarioId, UUID planId) {
        planConAcceso(usuarioId, planId);
        return ausenciaRepository.findByPlanIdOrderByReportadoEnAsc(planId.toString()).stream()
                .map(ImprevistoDtos.AusenciaResponse::from)
                .toList();
    }

    /**
     * El plazo configurado, pero sin pasar del inicio del plan: una votación
     * que cierra cuando el plan ya empezó decide tarde, y un MANTENER por
     * defecto llegaría con la gente ya en el sitio. Aun así se dejan al menos
     * cinco minutos, o un aviso a última hora cerraría sin que nadie lo viera.
     */
    private Instant calcularExpiracion(Plan plan, Instant ahora) {
        Instant limite = ahora.plus(Duration.ofMinutes(plazoMinutos));
        if (plan.getVentanaConfirmada() != null) {
            Instant inicio = ZonaHoraria.instante(
                    plan.getVentanaConfirmada().getFecha(), plan.getVentanaConfirmada().getHoraInicio());
            if (inicio.isBefore(limite)) {
                limite = inicio;
            }
        }
        Instant minimo = ahora.plus(PLAZO_MINIMO);
        return limite.isBefore(minimo) ? minimo : limite;
    }

    /* ------------------------------------------------------------------ *
     * Votar (RF-17)
     * ------------------------------------------------------------------ */

    /**
     * No es de solo lectura: si con este voto ya ha votado todo el que puede,
     * la votación se cierra aquí mismo y el resultado se aplica al plan.
     */
    @Transactional
    public ImprevistoDtos.VotacionExpresResponse votar(UUID usuarioId, UUID planId,
                                                       VotacionExpres.Opcion opcion) {
        Plan plan = planConAcceso(usuarioId, planId);

        VotacionExpres votacion = votacionRepository
                .findByPlanIdAndEstado(planId.toString(), VotacionExpres.Estado.ABIERTA)
                .orElseThrow(() -> new NotFoundException(
                        "No hay ninguna votación exprés abierta para este plan"));

        if (usuarioId.toString().equals(votacion.getUsuarioReporta())) {
            throw new BusinessException("Quien reporta el imprevisto no vota");
        }

        if (Instant.now().isAfter(votacion.getExpiraEn())) {
            // El barrido aún no ha pasado, pero el plazo ya venció: aceptar el
            // voto lo haría contar en una votación que debía estar cerrada.
            throw new BusinessException("El plazo de la votación exprés ya venció");
        }

        // Cambiar de opinión sustituye el voto anterior, no suma otro. Se escribe
        // solo ese voto y solo si la votación sigue abierta: ver
        // VotacionExpresOperaciones.
        VotacionExpres guardada = operaciones
                .registrarVoto(planId.toString(), usuarioId.toString(), opcion, Instant.now())
                .orElseThrow(() -> new BusinessException("La votación exprés ya cerró o venció"));

        /* Sin este aviso, el panel de votación era lo único urgente de la app que
           no se movía: el reloj corría, pero el recuento que veía cada miembro se
           quedaba en el que había al abrir la pantalla, y solo se enteraba de la
           realidad al cerrarse la votación. Solo se anuncia que cambió; cada
           cliente vuelve a pedir su propia vista, porque «mi voto» depende de
           quién pregunta y el topic lo lee el grupo entero (RNF-02). */
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("planId", planId.toString());
        datos.put("votosEmitidos", guardada.getVotos() == null ? 0 : guardada.getVotos().size());
        notificador.aGrupo(plan.getGrupo().getId(),
                EventoTiempoReal.Tipo.VOTO_EXPRES_ACTUALIZADO, datos);

        // Cierre anticipado: si ya votaron todos, esperar al plazo solo retrasa
        // una decisión que no va a cambiar. Pasa por el mismo cierre que el
        // barrido, con su reclamo atómico, así que no se aplica dos veces.
        if (guardada.votosEmitidos() >= guardada.votantesPosibles()) {
            VotacionExpres cerrada = cerrarYDevolver(guardada.getId())
                    .or(() -> votacionRepository.findById(guardada.getId()))
                    .orElse(guardada);
            return aRespuesta(cerrada, usuarioId);
        }

        return aRespuesta(guardada, usuarioId);
    }

    @Transactional(readOnly = true)
    public Optional<ImprevistoDtos.VotacionExpresResponse> abierta(UUID usuarioId, UUID planId) {
        planConAcceso(usuarioId, planId);
        return votacionRepository
                .findByPlanIdAndEstado(planId.toString(), VotacionExpres.Estado.ABIERTA)
                .map(v -> aRespuesta(v, usuarioId));
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
     * RF-18: sin quórum se aplica el resultado por defecto. El quórum es de dos
     * votos (o de todos los que pueden votar, si son menos; ver
     * {@link VotacionExpres#quorum()}), no la mitad del grupo: con un plazo de
     * una hora, exigir mayoría cancelaría planes por simple falta de atención,
     * pero un único voto tampoco debería poder cancelar el plan de seis.
     */
    @Transactional
    public void cerrar(String votacionId) {
        cerrarYDevolver(votacionId);
    }

    /** El cierre de verdad. Devuelve la votación cerrada, o vacío si ya la había cerrado otro. */
    private Optional<VotacionExpres> cerrarYDevolver(String votacionId) {
        Instant ahora = Instant.now();

        // Se marca CERRADA de forma atómica antes de contar. Si ya lo estaba,
        // otro proceso la cerró entre el barrido y esta llamada. Y a partir de
        // aquí ningún voto nuevo entra, así que el recuento es definitivo.
        VotacionExpres votacion = operaciones.reclamarParaCerrar(votacionId, ahora).orElse(null);
        if (votacion == null) {
            return Optional.empty();
        }

        /*
         * El aviso al grupo sale solo cuando el cambio del plan ya está guardado
         * en Postgres. Antes se mandaba dentro de la transacción: si el commit
         * fallaba, el grupo ya había leído "se canceló" con el plan aún
         * confirmado, y la votación quedaba cerrada en Mongo sin que nadie
         * volviera a aplicarla. Si la transacción no se confirma, se reabre y el
         * siguiente barrido lo reintenta.
         */
        AtomicReference<Map<String, Object>> aviso = new AtomicReference<>();
        UUID grupoId = UUID.fromString(votacion.getGrupoId());
        boolean conTransaccion = TransactionSynchronizationManager.isSynchronizationActive();

        if (conTransaccion) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        trasCerrar(grupoId, votacion, aviso.get());
                    } else {
                        log.warn("El cierre de la votación exprés {} no se guardó: se reabre", votacionId);
                        operaciones.reabrir(votacionId);
                    }
                }
            });
        }

        try {
            int emitidos = votacion.votosEmitidos();
            boolean sinQuorum = emitidos == 0 || emitidos < votacion.quorum();
            VotacionExpres.Opcion resultado = sinQuorum
                    ? resultadoPorDefecto
                    : masVotada(votacion.getVotos());

            votacion.setEstado(VotacionExpres.Estado.CERRADA);
            votacion.setCerradaEn(ahora);
            votacion.setResultado(resultado);
            votacion.setResultadoPorDefecto(sinQuorum);
            // Solo ahora se marca para purga: mientras estaba abierta, el índice
            // TTL la ignoraba porque este campo era nulo.
            votacion.setPurgarEn(ahora.plus(Duration.ofDays(diasPurga)));
            votacionRepository.save(votacion);

            aviso.set(aplicarAlPlan(votacion, resultado));

            log.info("Votación exprés {} cerrada con {}{}", votacionId, resultado,
                    sinQuorum ? " (por defecto, sin quórum: " + emitidos + " voto(s))" : "");
        } catch (RuntimeException ex) {
            // Sin transacción no hay afterCompletion que la reabra.
            if (!conTransaccion) {
                operaciones.reabrir(votacionId);
            }
            throw ex;
        }

        if (!conTransaccion) {
            trasCerrar(grupoId, votacion, aviso.get());
        }
        return Optional.of(votacion);
    }

    /**
     * Lo que sigue a un cierre ya guardado. Tras el commit, como el aviso: si
     * el cambio del plan no llega a Postgres la votación se reabre, y los
     * retrasos tienen que seguir ahí.
     *
     * Con CANCELAR o REAGENDAR la fecha a la que se llegaba tarde ya no
     * existe; los retrasos se quedaban en la vista del plan y reaparecían, con
     * sus minutos viejos, cuando el plan se volvía a confirmar.
     */
    private void trasCerrar(UUID grupoId, VotacionExpres votacion, Map<String, Object> datos) {
        if (votacion.getResultado() == VotacionExpres.Opcion.CANCELAR
                || votacion.getResultado() == VotacionExpres.Opcion.REAGENDAR) {
            try {
                alertaRetrasoRepository.deleteByPlanId(votacion.getPlanId());
            } catch (RuntimeException ex) {
                // El plan ya cambió: unos retrasos huérfanos no justifican
                // perder el aviso del resultado.
                log.warn("No se pudieron borrar los retrasos del plan {}: {}",
                        votacion.getPlanId(), ex.getMessage());
            }
        }
        avisarCierre(grupoId, datos);
    }

    private void avisarCierre(UUID grupoId, Map<String, Object> datos) {
        if (datos != null) {
            notificador.aGrupo(grupoId, EventoTiempoReal.Tipo.VOTACION_EXPRES_CERRADA, datos);
        }
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

    /** Aplica el resultado al plan y devuelve los datos del aviso, o `null` si el plan ya no existe. */
    private Map<String, Object> aplicarAlPlan(VotacionExpres votacion, VotacionExpres.Opcion resultado) {
        Plan plan = planRepository.findById(UUID.fromString(votacion.getPlanId())).orElse(null);
        if (plan == null) {
            return null;
        }

        // Solo sobre un plan que sigue confirmado: si mientras tanto se canceló,
        // un REAGENDAR no debe resucitarlo.
        if (plan.getEstado() == Plan.Estado.CONFIRMADO) {
            switch (resultado) {
                case CANCELAR -> plan.setEstado(Plan.Estado.CANCELADO);
                // El plan vuelve a coordinación: la fecha confirmada ya no vale,
                // pero el plan no se tira. EN_RECOORDINACION ya existía en el enum.
                case REAGENDAR -> {
                    plan.setEstado(Plan.Estado.EN_RECOORDINACION);
                    plan.setVentanaConfirmada(null);
                }
                // MANTENER no toca nada: sigue CONFIRMADO con su fecha.
                case MANTENER -> { }
            }
            planRepository.save(plan);
        }

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("planId", plan.getId().toString());
        datos.put("tituloPlan", plan.getTitulo());
        datos.put("resultado", resultado.name());
        datos.put("porDefecto", votacion.isResultadoPorDefecto());
        datos.put("estadoPlan", plan.getEstado().name());
        return datos;
    }

    /* ------------------------------------------------------------------ */

    /** Los campos de AusenciaResponse, para añadirla a la lista sin volver a pedirla. */
    private Map<String, Object> datosDeAusencia(Plan plan, Ausencia ausencia) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("planId", plan.getId().toString());
        datos.put("tituloPlan", plan.getTitulo());
        datos.put("usuarioId", ausencia.getUsuarioId());
        datos.put("nombreUsuario", ausencia.getNombreUsuario());
        datos.put("critica", ausencia.isCritica());
        datos.put("reportadoEn", ausencia.getReportadoEn().toString());
        if (ausencia.getMotivo() != null) {
            datos.put("motivo", ausencia.getMotivo());
        }
        return datos;
    }

    private Map<String, Object> datosDeVotacion(Plan plan, VotacionExpres votacion) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("planId", plan.getId().toString());
        datos.put("tituloPlan", plan.getTitulo());
        datos.put("votacionId", votacion.getId());
        // Quien la origina: su cliente no debe pedirse votar a sí mismo.
        datos.put("usuarioId", votacion.getUsuarioReporta());
        datos.put("nombreReporta", votacion.getNombreReporta());
        datos.put("razonCriticidad", votacion.getRazonCriticidad());
        datos.put("expiraEn", votacion.getExpiraEn().toString());
        if (votacion.getMotivo() != null) {
            datos.put("motivo", votacion.getMotivo());
        }
        return datos;
    }

    private ImprevistoDtos.VotacionExpresResponse aRespuesta(VotacionExpres votacion, UUID usuarioId) {
        return ImprevistoDtos.VotacionExpresResponse.from(votacion, usuarioId.toString(), resultadoPorDefecto);
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
