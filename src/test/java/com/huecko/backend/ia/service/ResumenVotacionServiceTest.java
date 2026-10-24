package com.huecko.backend.ia.service;

import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.ServicioNoDisponibleException;
import com.huecko.backend.grupo.dto.GrupoResponse;
import com.huecko.backend.grupo.dto.MiembroResponse;
import com.huecko.backend.grupo.service.GrupoService;
import com.huecko.backend.ia.ClienteIA;
import com.huecko.backend.ia.IaNoDisponibleException;
import com.huecko.backend.ia.dto.ResumenVotacionResponse;
import com.huecko.backend.imprevisto.dto.ImprevistoDtos;
import com.huecko.backend.imprevisto.service.ImprevistoService;
import com.huecko.backend.plan.dto.PlanResponse;
import com.huecko.backend.plan.dto.VentanaPlanResponse;
import com.huecko.backend.plan.service.PlanService;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.retraso.dto.RetrasoResponse;
import com.huecko.backend.retraso.service.RetrasoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** «Resumen con Huecko IA» de una votación, traído de la app móvil. */
@ExtendWith(MockitoExtension.class)
class ResumenVotacionServiceTest {

    private static final UUID YO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ANA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GRUPO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PLAN = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID SABADO = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID DOMINGO = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final Instant AHORA =
            LocalDateTime.of(2026, 10, 22, 10, 0).atZone(ZonaHoraria.ZONA).toInstant();
    private static final Instant PLAZO =
            LocalDateTime.of(2026, 10, 24, 7, 0).atZone(ZonaHoraria.ZONA).toInstant();

    @Mock
    private PlanService planService;
    @Mock
    private GrupoService grupoService;
    @Mock
    private ImprevistoService imprevistoService;
    @Mock
    private RetrasoService retrasoService;
    @Mock
    private ClienteIA clienteIA;
    @Mock
    private AccesoAsistente acceso;

    private ResumenVotacionService service() {
        return new ResumenVotacionService(planService, grupoService, imprevistoService, retrasoService,
                clienteIA, acceso, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static PlanResponse plan(Plan.Estado estado, boolean abierta, UUID confirmada) {
        return new PlanResponse(PLAN, GRUPO, "Repaso de Cálculo", "Biblioteca", YO, PLAZO, estado, true,
                List.of(
                        new VentanaPlanResponse(SABADO, LocalDate.of(2026, 10, 24), 6, LocalTime.of(8, 0),
                                LocalTime.of(10, 0), 100, 2, List.of(YO, ANA), true),
                        new VentanaPlanResponse(DOMINGO, LocalDate.of(2026, 10, 25), 7, LocalTime.of(8, 0),
                                LocalTime.of(10, 0), 67, 1, List.of(ANA), false)),
                confirmada, abierta, AHORA, null);
    }

    private void contexto(PlanResponse plan) {
        when(planService.detalle(YO, PLAN)).thenReturn(plan);
        when(grupoService.detalle(YO, GRUPO)).thenReturn(new GrupoResponse(GRUPO, "Proyecto", "", YO, 80, AHORA,
                List.of(new MiembroResponse(YO, "Alex", "a@h.com", MiembroGrupo.Rol.ORGANIZADOR, true),
                        new MiembroResponse(ANA, "Ana", "ana@h.com", MiembroGrupo.Rol.MIEMBRO, false),
                        new MiembroResponse(UUID.randomUUID(), "Luis", "l@h.com", MiembroGrupo.Rol.MIEMBRO, false)), null));
        when(imprevistoService.ausencias(YO, PLAN)).thenReturn(List.of(
                new ImprevistoDtos.AusenciaResponse(ANA.toString(), "Ana", "Viaje familiar", AHORA, true)));
        when(retrasoService.listar(YO, PLAN)).thenReturn(List.of(
                new RetrasoResponse(YO.toString(), "Alex", 15, AHORA, false)));
        when(imprevistoService.abierta(YO, PLAN)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Le cuenta a la IA votos, opciones, ausencias y retrasos, y le dice qué puede recomendar")
    void loQueVeLaIA() {
        contexto(plan(Plan.Estado.PROPUESTO, true, null));
        when(clienteIA.resumenVotacion(any())).thenReturn(new ClienteIA.RespuestaResumen(
                "Votaron 2 de 3 y el sábado va ganando.", "CERRAR", "el sábado tiene todos los votos"));
        ArgumentCaptor<ClienteIA.PeticionResumen> enviada = ArgumentCaptor.forClass(ClienteIA.PeticionResumen.class);

        ResumenVotacionResponse r = service().resumir(YO, PLAN);

        verify(clienteIA).resumenVotacion(enviada.capture());
        ClienteIA.PeticionResumen p = enviada.getValue();
        assertThat(p.estado()).isEqualTo("EN_VOTACION");
        assertThat(p.integrantes()).isEqualTo(3);
        assertThat(p.votantes()).isEqualTo(2);
        assertThat(p.cierre()).isEqualTo("sábado 24 de octubre, 07:00");
        assertThat(p.ventanas()).containsExactly(
                new ClienteIA.VentanaVotadaIA("sábado 24/10 08:00-10:00", 2, 100, false),
                new ClienteIA.VentanaVotadaIA("domingo 25/10 08:00-10:00", 1, 67, false));
        assertThat(p.ausencias()).containsExactly(new ClienteIA.AusenciaIA("Ana", true, "Viaje familiar"));
        assertThat(p.retrasos()).containsExactly(new ClienteIA.RetrasoIA("Alex", 15));
        assertThat(p.opciones()).containsExactly("CERRAR", "ESPERAR");
        assertThat(r).isEqualTo(new ResumenVotacionResponse(
                "Votaron 2 de 3 y el sábado va ganando.", "CERRAR", "el sábado tiene todos los votos"));
        verify(acceso).exigir(YO);
    }

    @Test
    @DisplayName("Lo que admite cada estado: cerrar o esperar al votar; mantener o reprogramar ya confirmado")
    void opcionesPorEstado() {
        assertThat(ResumenVotacionService.opcionesPara(plan(Plan.Estado.PROPUESTO, true, null)))
                .containsExactly("CERRAR", "ESPERAR");
        assertThat(ResumenVotacionService.opcionesPara(plan(Plan.Estado.PROPUESTO, false, null)))
                .containsExactly("CERRAR");
        assertThat(ResumenVotacionService.opcionesPara(plan(Plan.Estado.CONFIRMADO, false, SABADO)))
                .containsExactly("MANTENER", "REPROGRAMAR");
        assertThat(ResumenVotacionService.opcionesPara(plan(Plan.Estado.EN_RECOORDINACION, false, null)))
                .containsExactly("REPROGRAMAR");
        assertThat(ResumenVotacionService.opcionesPara(plan(Plan.Estado.CANCELADO, false, null))).isEmpty();
    }

    @Test
    @DisplayName("La opción confirmada va marcada como elegida")
    void marcaLaElegida() {
        contexto(plan(Plan.Estado.CONFIRMADO, false, SABADO));
        when(clienteIA.resumenVotacion(any())).thenReturn(new ClienteIA.RespuestaResumen(
                "Se eligió el sábado.", "MANTENER", "nadie imprescindible avisó que falta"));
        ArgumentCaptor<ClienteIA.PeticionResumen> enviada = ArgumentCaptor.forClass(ClienteIA.PeticionResumen.class);

        service().resumir(YO, PLAN);

        verify(clienteIA).resumenVotacion(enviada.capture());
        assertThat(enviada.getValue().ventanas()).extracting(ClienteIA.VentanaVotadaIA::elegida).containsExactly(true, false);
    }

    @Test
    @DisplayName("Una recomendación que el estado no admite se quita, pero el resumen se enseña igual")
    void recomendacionNoAdmitida() {
        contexto(plan(Plan.Estado.PROPUESTO, true, null));
        when(clienteIA.resumenVotacion(any())).thenReturn(new ClienteIA.RespuestaResumen(
                "Votaron 2 de 3.", "REPROGRAMAR", "hay una ausencia crítica"));

        ResumenVotacionResponse r = service().resumir(YO, PLAN);

        assertThat(r).isEqualTo(new ResumenVotacionResponse("Votaron 2 de 3.", null, null));
    }

    @Test
    @DisplayName("Un plan cancelado no tiene votación que resumir, y no se gasta cupo")
    void cancelado() {
        when(planService.detalle(YO, PLAN)).thenReturn(plan(Plan.Estado.CANCELADO, false, null));

        assertThatThrownBy(() -> service().resumir(YO, PLAN)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("cancelado");
        verifyNoInteractions(acceso, clienteIA);
    }

    @Test
    @DisplayName("Si la IA no responde, 503")
    void iaCaida() {
        contexto(plan(Plan.Estado.PROPUESTO, true, null));
        when(clienteIA.resumenVotacion(any())).thenThrow(new IaNoDisponibleException("/v1/planes/resumen: 502", null));

        assertThatThrownBy(() -> service().resumir(YO, PLAN)).isInstanceOf(ServicioNoDisponibleException.class);
    }
}
