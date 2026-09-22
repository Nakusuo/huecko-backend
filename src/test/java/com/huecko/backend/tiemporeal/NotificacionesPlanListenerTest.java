package com.huecko.backend.tiemporeal;

import com.huecko.backend.plan.event.PlanCambiadoEvent;
import com.huecko.backend.plan.event.PlanCerradoEvent;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.tiemporeal.dto.EventoTiempoReal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** RF-11: qué se manda al grupo cuando una votación termina. */
class NotificacionesPlanListenerTest {

    private static final UUID GRUPO = UUID.randomUUID();
    private static final UUID PLAN = UUID.randomUUID();

    private NotificadorTiempoReal notificador;
    private NotificacionesPlanListener listener;

    @BeforeEach
    void setUp() {
        notificador = mock(NotificadorTiempoReal.class);
        listener = new NotificacionesPlanListener(notificador);
    }

    @Test
    @DisplayName("Un plan confirmado viaja con fecha, hora de inicio y de fin")
    void planConfirmado() {
        listener.alCerrarLaVotacion(new PlanCerradoEvent(
                GRUPO, PLAN, "Repaso de Cálculo", "Biblioteca", Plan.Estado.CONFIRMADO,
                LocalDate.of(2026, 9, 16), LocalTime.of(16, 0), LocalTime.of(18, 0)));

        Map<String, Object> datos = datosPublicados(EventoTiempoReal.Tipo.PLAN_CONFIRMADO);

        assertThat(datos)
                .containsEntry("planId", PLAN.toString())
                .containsEntry("titulo", "Repaso de Cálculo")
                .containsEntry("lugar", "Biblioteca")
                .containsEntry("fecha", "2026-09-16")
                .containsEntry("horaInicio", "16:00")
                .containsEntry("horaFin", "18:00");
    }

    @Test
    @DisplayName("El lugar es opcional (RF-08) y su ausencia no rompe el aviso")
    void planConfirmadoSinLugar() {
        listener.alCerrarLaVotacion(new PlanCerradoEvent(
                GRUPO, PLAN, "Cena", null, Plan.Estado.CONFIRMADO,
                LocalDate.of(2026, 9, 16), LocalTime.of(21, 0), LocalTime.of(23, 0)));

        assertThat(datosPublicados(EventoTiempoReal.Tipo.PLAN_CONFIRMADO))
                .doesNotContainKey("lugar")
                .containsEntry("titulo", "Cena");
    }

    @Test
    @DisplayName("Un plan cancelado avisa con motivo y sin fecha")
    void planCancelado() {
        listener.alCerrarLaVotacion(new PlanCerradoEvent(
                GRUPO, PLAN, "Cena", null, Plan.Estado.CANCELADO, null, null, null));

        Map<String, Object> datos = datosPublicados(EventoTiempoReal.Tipo.PLAN_CANCELADO);

        assertThat(datos).containsKey("motivo").doesNotContainKey("fecha");
    }

    @Test
    @DisplayName("PLAN_PROPUESTO lleva el plan, quien lo propuso y el plazo de votación")
    void planPropuesto() {
        UUID ana = UUID.randomUUID();
        Instant plazo = Instant.parse("2026-09-22T18:00:00Z");
        listener.alCambiarElPlan(new PlanCambiadoEvent(
                PlanCambiadoEvent.Cambio.PROPUESTO, GRUPO, PLAN, "Cena", ana, plazo));

        assertThat(datosPublicados(EventoTiempoReal.Tipo.PLAN_PROPUESTO))
                .containsEntry("planId", PLAN.toString())
                .containsEntry("titulo", "Cena")
                .containsEntry("usuarioId", ana.toString())
                .containsEntry("plazoVotacion", "2026-09-22T18:00:00Z");
    }

    @Test
    @DisplayName("PLAN_REAGENDADO lleva el plazo nuevo")
    void planReagendado() {
        UUID ana = UUID.randomUUID();
        listener.alCambiarElPlan(new PlanCambiadoEvent(
                PlanCambiadoEvent.Cambio.REAGENDADO, GRUPO, PLAN, "Cena", ana,
                Instant.parse("2026-09-25T10:00:00Z")));

        assertThat(datosPublicados(EventoTiempoReal.Tipo.PLAN_REAGENDADO))
                .containsEntry("usuarioId", ana.toString())
                .containsEntry("plazoVotacion", "2026-09-25T10:00:00Z");
    }

    @Test
    @DisplayName("VOTO_ACTUALIZADO lleva el plan y quien votó, nunca qué ventana")
    void votoActualizado() {
        UUID bruno = UUID.randomUUID();
        listener.alCambiarElPlan(new PlanCambiadoEvent(
                PlanCambiadoEvent.Cambio.VOTO_ACTUALIZADO, GRUPO, PLAN, "Cena", bruno,
                Instant.parse("2026-09-25T10:00:00Z")));

        assertThat(datosPublicados(EventoTiempoReal.Tipo.VOTO_ACTUALIZADO))
                .containsOnlyKeys("planId", "titulo", "usuarioId")
                .containsEntry("usuarioId", bruno.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> datosPublicados(EventoTiempoReal.Tipo tipoEsperado) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(notificador).aGrupo(eq(GRUPO), eq(tipoEsperado), captor.capture());
        return captor.getValue();
    }
}
