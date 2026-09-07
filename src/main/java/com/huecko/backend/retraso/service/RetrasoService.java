package com.huecko.backend.retraso.service;

import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.mongo.document.AlertaRetraso;
import com.huecko.backend.mongo.repository.AlertaRetrasoRepository;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.retraso.dto.RetrasoResponse;
import com.huecko.backend.tiemporeal.NotificadorTiempoReal;
import com.huecko.backend.tiemporeal.dto.EventoTiempoReal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Módulo 4: gestión de retrasos (HU-11, HU-12).
 *
 * <b>El evento no cambia.</b> Es el criterio de aceptación de HU-11 y lo que
 * separa este módulo del 5: avisar de que llegas tarde informa, no reabre la
 * coordinación. Aquí no se toca el plan ni una sola vez.
 */
@Service
@RequiredArgsConstructor
public class RetrasoService {

    private final AlertaRetrasoRepository alertaRepository;
    private final PlanRepository planRepository;
    private final MiembroGrupoRepository miembroGrupoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificadorTiempoReal notificador;

    /**
     * RF-12 y RF-13: registra el retraso y avisa al grupo.
     *
     * Volver a llamar actualiza la estimación en vez de añadir otra alerta:
     * quien dijo 10 minutos y ve que serán 25 corrige, y la vista del evento
     * sigue mostrando una sola cifra por persona.
     */
    @Transactional(readOnly = true)
    public RetrasoResponse reportar(UUID usuarioId, UUID planId, int minutosEstimados) {
        Plan plan = planConAcceso(usuarioId, planId);
        exigirConfirmado(plan);

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NotFoundException("El usuario del token ya no existe"));

        Instant ahora = Instant.now();
        AlertaRetraso alerta = alertaRepository
                .findByPlanIdAndUsuarioId(planId.toString(), usuarioId.toString())
                .orElseGet(() -> AlertaRetraso.builder()
                        .planId(planId.toString())
                        .grupoId(plan.getGrupo().getId().toString())
                        .usuarioId(usuarioId.toString())
                        .creadoEn(ahora)
                        .build());

        alerta.setNombreUsuario(usuario.getNombre());
        alerta.setMinutosEstimados(minutosEstimados);
        alerta.setActualizadoEn(ahora);

        AlertaRetraso guardada = alertaRepository.save(alerta);

        // Se avisa después de guardar y con llamada directa, no con evento de
        // dominio: la escritura va a Mongo, fuera de la transacción JPA, y un
        // @TransactionalEventListener(AFTER_COMMIT) nunca llegaría a dispararse.
        notificador.aGrupo(plan.getGrupo().getId(),
                EventoTiempoReal.Tipo.RETRASO_REPORTADO,
                datosDe(plan, guardada));

        return RetrasoResponse.from(guardada);
    }

    /** RF-14: quién llega tarde y cuánto, sin leer el historial del chat. */
    @Transactional(readOnly = true)
    public List<RetrasoResponse> listar(UUID usuarioId, UUID planId) {
        planConAcceso(usuarioId, planId);
        return alertaRepository.findByPlanIdOrderByCreadoEnAsc(planId.toString()).stream()
                .map(RetrasoResponse::from)
                .toList();
    }

    /** Retirar el aviso: al final se llega a tiempo. */
    @Transactional(readOnly = true)
    public void retirar(UUID usuarioId, UUID planId) {
        Plan plan = planConAcceso(usuarioId, planId);
        alertaRepository.deleteByPlanIdAndUsuarioId(planId.toString(), usuarioId.toString());

        notificador.aGrupo(plan.getGrupo().getId(),
                EventoTiempoReal.Tipo.RETRASO_REPORTADO,
                Map.of("planId", planId.toString(),
                        "usuarioId", usuarioId.toString(),
                        "retirado", true));
    }

    /* ------------------------------------------------------------------ */

    private Map<String, Object> datosDe(Plan plan, AlertaRetraso alerta) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("planId", plan.getId().toString());
        datos.put("tituloPlan", plan.getTitulo());
        datos.put("usuarioId", alerta.getUsuarioId());
        datos.put("nombreUsuario", alerta.getNombreUsuario());
        datos.put("minutosEstimados", alerta.getMinutosEstimados());
        datos.put("retirado", false);
        return datos;
    }

    /**
     * CU-04 exige que el evento esté confirmado. Sin esta comprobación se
     * podría avisar de un retraso a un plan que todavía se está votando, es
     * decir a una hora que aún no existe.
     */
    private void exigirConfirmado(Plan plan) {
        if (plan.getEstado() != Plan.Estado.CONFIRMADO) {
            throw new BusinessException(
                    "Solo se puede avisar de un retraso en un plan ya confirmado");
        }
    }

    private Plan planConAcceso(UUID usuarioId, UUID planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException("El plan no existe"));

        // Mismo mensaje que si no existiera: distinguirlos permitiría averiguar
        // qué planes hay probando identificadores.
        if (!miembroGrupoRepository.existsByGrupo_IdAndUsuario_Id(
                plan.getGrupo().getId(), usuarioId)) {
            throw new NotFoundException("El plan no existe");
        }
        return plan;
    }
}
