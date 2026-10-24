package com.huecko.backend.recordatorio;

import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.llegada.service.LlegadaService;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.tiemporeal.Recordatorio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Recordatorios al grupo: una hora antes de un plan confirmado y una hora
 * antes de que cierre una votación. Cada uno sale una sola vez (lo marca la
 * columna del plan) aunque el barrido pase muchas veces.
 *
 * Solo llegan a quien tenga Huecko abierto: el aviso va por el canal en vivo,
 * no hay notificaciones push.
 */
@Service
public class RecordatoriosService {

    static final Duration ANTELACION_PLAN = Duration.ofHours(1);
    static final Duration ANTELACION_CIERRE = Duration.ofHours(1);

    private final PlanRepository planes;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    @Autowired
    public RecordatoriosService(PlanRepository planes, ApplicationEventPublisher eventos) {
        this(planes, eventos, Clock.systemUTC());
    }

    RecordatoriosService(PlanRepository planes, ApplicationEventPublisher eventos, Clock reloj) {
        this.planes = planes;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /** Devuelve cuántos recordatorios envió. */
    @Transactional
    public int enviarPendientes() {
        Instant ahora = reloj.instant();
        LocalDate hoy = LocalDate.ofInstant(ahora, ZonaHoraria.ZONA);
        int enviados = 0;

        // Hoy y mañana: un plan a las 00:30 de mañana se recuerda hoy a las 23:30.
        for (Plan plan : planes.confirmadosSinRecordatorio(hoy, hoy.plusDays(1))) {
            Instant inicio = LlegadaService.inicio(plan);
            if (inicio != null && inicio.isAfter(ahora) && !inicio.isAfter(ahora.plus(ANTELACION_PLAN))) {
                eventos.publishEvent(new Recordatorio(Recordatorio.Tipo.EMPIEZA_PRONTO, plan.getGrupo().getId(),
                        plan.getId(), plan.getTitulo(), plan.getLugar(), inicio));
                plan.setRecordatorioEnviadoEn(ahora);
                enviados++;
            }
        }

        for (Plan plan : planes.votacionesPorCerrarSinAviso(ahora, ahora.plus(ANTELACION_CIERRE))) {
            eventos.publishEvent(new Recordatorio(Recordatorio.Tipo.CIERRA_PRONTO, plan.getGrupo().getId(),
                    plan.getId(), plan.getTitulo(), plan.getLugar(), plan.getPlazoVotacion()));
            plan.setAvisoCierreEnviadoEn(ahora);
            enviados++;
        }
        return enviados;
    }
}
