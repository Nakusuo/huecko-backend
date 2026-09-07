package com.huecko.backend.retraso.service;

import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.mongo.document.AlertaRetraso;
import com.huecko.backend.mongo.repository.AlertaRetrasoRepository;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.retraso.dto.RetrasoResponse;
import com.huecko.backend.tiemporeal.NotificadorTiempoReal;
import com.huecko.backend.tiemporeal.dto.EventoTiempoReal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Módulo 4 con los repositorios simulados.
 *
 * La regla que más importa aquí es que el plan no se toca: avisar de un
 * retraso informa, no reabre la coordinación. Si algún día alguien añade un
 * `planRepository.save` en este servicio, la prueba correspondiente lo caza.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetrasoServiceTest {

    private static final UUID GRUPO = UUID.randomUUID();
    private static final UUID PLAN = UUID.randomUUID();

    @Mock private AlertaRetrasoRepository alertaRepository;
    @Mock private PlanRepository planRepository;
    @Mock private MiembroGrupoRepository miembroGrupoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private NotificadorTiempoReal notificador;

    private RetrasoService servicio;
    private Usuario ana;

    @BeforeEach
    void preparar() {
        servicio = new RetrasoService(alertaRepository, planRepository,
                miembroGrupoRepository, usuarioRepository, notificador);

        ana = Usuario.builder().id(UUID.randomUUID()).nombre("Ana").email("ana@huecko.app").build();
        when(usuarioRepository.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(miembroGrupoRepository.existsByGrupo_IdAndUsuario_Id(eq(GRUPO), any())).thenReturn(true);
        when(alertaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(alertaRepository.findByPlanIdAndUsuarioId(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("RF-12: un retraso se guarda con los minutos y el nombre de quien avisa")
    void seReportaUnRetraso() {
        planEnEstado(Plan.Estado.CONFIRMADO);

        RetrasoResponse r = servicio.reportar(ana.getId(), PLAN, 15);

        assertThat(r.minutosEstimados()).isEqualTo(15);
        assertThat(r.nombreUsuario()).isEqualTo("Ana");
        assertThat(r.corregido()).isFalse();
    }

    @Test
    @DisplayName("RF-13: se avisa al grupo por el canal en tiempo real")
    void seAvisaAlGrupo() {
        planEnEstado(Plan.Estado.CONFIRMADO);

        servicio.reportar(ana.getId(), PLAN, 20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(notificador).aGrupo(eq(GRUPO),
                eq(EventoTiempoReal.Tipo.RETRASO_REPORTADO), datos.capture());

        assertThat(datos.getValue())
                .containsEntry("minutosEstimados", 20)
                .containsEntry("nombreUsuario", "Ana")
                .containsEntry("retirado", false);
    }

    @Test
    @DisplayName("RF-13: el estado del evento NO cambia al avisar de un retraso")
    void elPlanNoSeToca() {
        Plan plan = planEnEstado(Plan.Estado.CONFIRMADO);

        servicio.reportar(ana.getId(), PLAN, 30);

        assertThat(plan.getEstado()).isEqualTo(Plan.Estado.CONFIRMADO);
        verify(planRepository, never()).save(any());
    }

    @Test
    @DisplayName("Volver a avisar corrige la estimacion en vez de crear otra alerta")
    void reportarDosVecesCorrige() {
        planEnEstado(Plan.Estado.CONFIRMADO);
        Instant antes = Instant.now().minusSeconds(600);
        when(alertaRepository.findByPlanIdAndUsuarioId(PLAN.toString(), ana.getId().toString()))
                .thenReturn(Optional.of(AlertaRetraso.builder()
                        .id("a-1")
                        .planId(PLAN.toString())
                        .grupoId(GRUPO.toString())
                        .usuarioId(ana.getId().toString())
                        .nombreUsuario("Ana")
                        .minutosEstimados(10)
                        .creadoEn(antes)
                        .actualizadoEn(antes)
                        .build()));

        RetrasoResponse r = servicio.reportar(ana.getId(), PLAN, 25);

        assertThat(r.minutosEstimados()).isEqualTo(25);
        assertThat(r.corregido()).isTrue();
        // Se guarda el mismo documento, no uno nuevo.
        ArgumentCaptor<AlertaRetraso> guardada = ArgumentCaptor.forClass(AlertaRetraso.class);
        verify(alertaRepository).save(guardada.capture());
        assertThat(guardada.getValue().getId()).isEqualTo("a-1");
        assertThat(guardada.getValue().getCreadoEn()).isEqualTo(antes);
    }

    @Test
    @DisplayName("CU-04: no se puede avisar de un retraso a un plan que aun se vota")
    void elPlanPropuestoNoAdmiteRetrasos() {
        planEnEstado(Plan.Estado.PROPUESTO);

        assertThatThrownBy(() -> servicio.reportar(ana.getId(), PLAN, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("confirmado");

        verify(notificador, never()).aGrupo(any(), any(), anyMap());
    }

    @Test
    @DisplayName("Quien no pertenece al grupo no puede avisar ni ver los retrasos")
    void elAjenoNoEntra() {
        planEnEstado(Plan.Estado.CONFIRMADO);
        UUID intruso = UUID.randomUUID();
        when(miembroGrupoRepository.existsByGrupo_IdAndUsuario_Id(GRUPO, intruso)).thenReturn(false);

        assertThatThrownBy(() -> servicio.reportar(intruso, PLAN, 10))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> servicio.listar(intruso, PLAN))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("RF-14: se listan los retrasos en orden de aviso")
    void seListanLosRetrasos() {
        planEnEstado(Plan.Estado.CONFIRMADO);
        when(alertaRepository.findByPlanIdOrderByCreadoEnAsc(PLAN.toString())).thenReturn(List.of(
                alerta("Ana", 10), alerta("Bruno", 25)));

        List<RetrasoResponse> lista = servicio.listar(ana.getId(), PLAN);

        assertThat(lista).extracting(RetrasoResponse::nombreUsuario)
                .containsExactly("Ana", "Bruno");
    }

    @Test
    @DisplayName("Retirar el aviso lo borra y lo comunica al grupo")
    void seRetiraElAviso() {
        planEnEstado(Plan.Estado.CONFIRMADO);

        servicio.retirar(ana.getId(), PLAN);

        verify(alertaRepository).deleteByPlanIdAndUsuarioId(PLAN.toString(), ana.getId().toString());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(notificador).aGrupo(eq(GRUPO),
                eq(EventoTiempoReal.Tipo.RETRASO_REPORTADO), datos.capture());
        assertThat(datos.getValue()).containsEntry("retirado", true);
    }

    /* ------------------------------------------------------------------ */

    private Plan planEnEstado(Plan.Estado estado) {
        Plan plan = Plan.builder()
                .id(PLAN)
                .grupo(Grupo.builder().id(GRUPO).nombre("Los de siempre").build())
                .titulo("Cena")
                .estado(estado)
                .build();
        when(planRepository.findById(PLAN)).thenReturn(Optional.of(plan));
        return plan;
    }

    private AlertaRetraso alerta(String nombre, int minutos) {
        Instant ahora = Instant.now();
        return AlertaRetraso.builder()
                .planId(PLAN.toString())
                .grupoId(GRUPO.toString())
                .usuarioId(UUID.randomUUID().toString())
                .nombreUsuario(nombre)
                .minutosEstimados(minutos)
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .build();
    }
}
