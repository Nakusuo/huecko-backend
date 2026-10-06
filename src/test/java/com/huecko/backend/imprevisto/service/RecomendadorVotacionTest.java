package com.huecko.backend.imprevisto.service;

import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.ia.ClienteIA;
import com.huecko.backend.ia.IaNoDisponibleException;
import com.huecko.backend.mongo.document.VotacionExpres;
import com.huecko.backend.mongo.document.VotacionExpres.Opcion;
import com.huecko.backend.mongo.repository.VotacionExpresOperaciones;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.VentanaPlan;
import com.huecko.backend.tiemporeal.NotificadorTiempoReal;
import com.huecko.backend.tiemporeal.dto.EventoTiempoReal;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecomendadorVotacionTest {

    private static final UUID GRUPO = UUID.randomUUID();

    private final ClienteIA ia = mock(ClienteIA.class);
    private final VotacionExpresOperaciones operaciones = mock(VotacionExpresOperaciones.class);
    private final NotificadorTiempoReal notificador = mock(NotificadorTiempoReal.class);

    private RecomendadorVotacion recomendador(boolean activo) {
        return new RecomendadorVotacion(ia, operaciones, notificador, activo, Runnable::run);
    }

    @Test
    void guardaLaSugerenciaYAvisaAlGrupo() {
        when(ia.recomendacion(any())).thenReturn(
                new ClienteIA.RespuestaRecomendacion("REAGENDAR", " sin las entradas no se puede entrar "));
        when(operaciones.guardarRecomendacion("v1", Opcion.REAGENDAR, "sin las entradas no se puede entrar"))
                .thenReturn(true);

        recomendador(true).recomendar(plan(null), votacion(), Opcion.MANTENER);

        verify(notificador).aGrupo(eq(GRUPO), eq(EventoTiempoReal.Tipo.VOTO_EXPRES_ACTUALIZADO), anyMap());
    }

    @Test
    void apagadoNoLlamaALaIA() {
        recomendador(false).recomendar(plan(null), votacion(), Opcion.MANTENER);

        verifyNoInteractions(ia, operaciones, notificador);
    }

    @Test
    void siLaIaFallaNoHaySugerenciaNiAviso() {
        when(ia.recomendacion(any())).thenThrow(new IaNoDisponibleException("caída", null));

        recomendador(true).recomendar(plan(null), votacion(), Opcion.MANTENER);

        verify(operaciones, never()).guardarRecomendacion(any(), any(), any());
        verifyNoInteractions(notificador);
    }

    @Test
    void unaOpcionQueNoExisteNoSeGuarda() {
        when(ia.recomendacion(any())).thenReturn(new ClienteIA.RespuestaRecomendacion("POSPONER", "lo que sea aquí"));

        recomendador(true).recomendar(plan(null), votacion(), Opcion.MANTENER);

        verify(operaciones, never()).guardarRecomendacion(any(), any(), any());
    }

    @Test
    void siLaVotacionYaCerroNoSeAvisa() {
        when(ia.recomendacion(any())).thenReturn(
                new ClienteIA.RespuestaRecomendacion("MANTENER", "el grupo puede seguir sin esa persona"));
        when(operaciones.guardarRecomendacion(any(), any(), any())).thenReturn(false);

        recomendador(true).recomendar(plan(null), votacion(), Opcion.MANTENER);

        verifyNoInteractions(notificador);
    }

    @Test
    void laPeticionLlevaCuandoEmpiezaYCuantoFalta() {
        LocalDate dia = LocalDate.of(2026, 10, 10);
        VentanaPlan ventana = VentanaPlan.builder().fecha(dia)
                .horaInicio(LocalTime.of(20, 0)).horaFin(LocalTime.of(23, 0)).build();
        var ahora = ZonaHoraria.instante(dia, LocalTime.of(17, 30));

        var p = RecomendadorVotacion.armarPeticion(plan(ventana), votacion(), Opcion.MANTENER, ahora);

        assertThat(p.inicio()).isEqualTo("2026-10-10T20:00");
        assertThat(p.horasHastaElPlan()).isEqualTo(2.5);
        assertThat(p.motivo()).isEqualTo("Tengo las entradas");
        assertThat(p.resultadoPorDefecto()).isEqualTo("MANTENER");
    }

    private static Plan plan(VentanaPlan ventana) {
        return Plan.builder().id(UUID.randomUUID()).titulo("Concierto").lugar("Estadio")
                .grupo(Grupo.builder().id(GRUPO).nombre("G").build())
                .ventanaConfirmada(ventana)
                .build();
    }

    private static VotacionExpres votacion() {
        return VotacionExpres.builder().id("v1").planId("p1").grupoId(GRUPO.toString())
                .motivo("Tengo las entradas").razonCriticidad("tiene las entradas del grupo")
                .estado(VotacionExpres.Estado.ABIERTA).votos(new LinkedHashMap<>())
                .miembrosDelGrupo(5)
                .build();
    }
}
