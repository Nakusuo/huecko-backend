package com.huecko.backend.imprevisto.service;

import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.ia.ClienteIA;
import com.huecko.backend.mongo.document.VotacionExpres;
import com.huecko.backend.mongo.repository.VotacionExpresOperaciones;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.VentanaPlan;
import com.huecko.backend.tiemporeal.NotificadorTiempoReal;
import com.huecko.backend.tiemporeal.dto.EventoTiempoReal;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pide a la IA qué opción conviene en una votación exprés recién abierta.
 *
 * <p>Corre en segundo plano: quien avisa de su ausencia no espera a la IA.
 * Cuando llega la sugerencia se guarda y se manda {@code VOTO_EXPRES_ACTUALIZADO}
 * para que cada cliente vuelva a pedir la votación. Si la IA falla, no hay
 * sugerencia y nada más: votar no depende de ella.
 *
 * <p>Se enciende con {@code huecko.ia.recomendaciones=true}.
 */
@Component
public class RecomendadorVotacion {

    private static final Logger log = LoggerFactory.getLogger(RecomendadorVotacion.class);

    private final ClienteIA ia;
    private final VotacionExpresOperaciones operaciones;
    private final NotificadorTiempoReal notificador;
    private final boolean activo;
    private final Executor segundoPlano;

    @Autowired
    public RecomendadorVotacion(ClienteIA ia, VotacionExpresOperaciones operaciones,
                                NotificadorTiempoReal notificador,
                                @Value("${huecko.ia.recomendaciones:false}") boolean activo) {
        this(ia, operaciones, notificador, activo, Executors.newFixedThreadPool(2, tarea -> {
            Thread hilo = new Thread(tarea, "recomendador-ia");
            hilo.setDaemon(true);
            return hilo;
        }));
    }

    /** Las pruebas pasan un ejecutor que corre la tarea en el acto. */
    RecomendadorVotacion(ClienteIA ia, VotacionExpresOperaciones operaciones,
                         NotificadorTiempoReal notificador, boolean activo, Executor segundoPlano) {
        this.ia = ia;
        this.operaciones = operaciones;
        this.notificador = notificador;
        this.activo = activo;
        this.segundoPlano = segundoPlano;
    }

    /**
     * Los datos del plan se leen aquí, en el hilo de la petición: la ventana
     * confirmada es perezosa y fuera de la sesión de JPA no se puede cargar.
     */
    public void recomendar(Plan plan, VotacionExpres votacion, VotacionExpres.Opcion resultadoPorDefecto) {
        if (!activo) {
            return;
        }
        ClienteIA.PeticionRecomendacion peticion = armarPeticion(plan, votacion, resultadoPorDefecto, Instant.now());
        UUID grupoId = plan.getGrupo().getId();
        String planId = votacion.getPlanId();
        String votacionId = votacion.getId();

        segundoPlano.execute(() -> pedirYGuardar(peticion, grupoId, planId, votacionId));
    }

    private void pedirYGuardar(ClienteIA.PeticionRecomendacion peticion, UUID grupoId,
                               String planId, String votacionId) {
        try {
            ClienteIA.RespuestaRecomendacion r = ia.recomendacion(peticion);
            VotacionExpres.Opcion opcion = VotacionExpres.Opcion.valueOf(r.opcion());
            if (r.razon() == null || r.razon().isBlank()) {
                throw new IllegalArgumentException("sugerencia sin razón");
            }
            if (!operaciones.guardarRecomendacion(votacionId, opcion, r.razon().strip())) {
                return;
            }
            Map<String, Object> datos = new LinkedHashMap<>();
            datos.put("planId", planId);
            datos.put("votacionId", votacionId);
            notificador.aGrupo(grupoId, EventoTiempoReal.Tipo.VOTO_EXPRES_ACTUALIZADO, datos);
        } catch (RuntimeException ex) {
            log.warn("Votación {} sin sugerencia de la IA: {}", votacionId, ex.getMessage());
        }
    }

    static ClienteIA.PeticionRecomendacion armarPeticion(Plan plan, VotacionExpres votacion,
                                                        VotacionExpres.Opcion resultadoPorDefecto, Instant ahora) {
        VentanaPlan ventana = plan.getVentanaConfirmada();
        String inicio = null;
        Double horas = null;
        if (ventana != null) {
            inicio = ventana.getFecha() + "T" + ventana.getHoraInicio();
            Instant empieza = ZonaHoraria.instante(ventana.getFecha(), ventana.getHoraInicio());
            // Horas con un decimal: al modelo le basta saber si son 2 o 30.
            horas = Math.round(Duration.between(ahora, empieza).toMinutes() / 6.0) / 10.0;
        }
        return new ClienteIA.PeticionRecomendacion(plan.getTitulo(), plan.getLugar(), inicio, horas,
                votacion.getMotivo(), votacion.getRazonCriticidad(), votacion.votantesPosibles(),
                resultadoPorDefecto.name());
    }

    @PreDestroy
    void apagar() {
        if (segundoPlano instanceof ExecutorService servicio) {
            servicio.shutdownNow();
        }
    }
}
