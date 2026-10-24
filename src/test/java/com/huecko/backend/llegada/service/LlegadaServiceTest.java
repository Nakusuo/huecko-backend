package com.huecko.backend.llegada.service;

import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.llegada.dto.LlegadasResponse;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.Llegada;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.entity.VentanaPlan;
import com.huecko.backend.postgres.repository.LlegadaRepository;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.tiemporeal.LlegadaRegistrada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** «Llegué»: solo cerca de la hora del plan, una vez, y a tiempo hasta diez minutos tarde. */
class LlegadaServiceTest {

    private static final LocalDate SABADO = LocalDate.of(2026, 10, 24);

    private final PlanRepository planes = mock(PlanRepository.class);
    private final LlegadaRepository llegadas = mock(LlegadaRepository.class);
    private final MiembroGrupoRepository miembros = mock(MiembroGrupoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);

    private final Grupo grupo = Grupo.builder().id(UUID.randomUUID()).nombre("Proyecto").build();
    private final Usuario ana = Usuario.builder().id(UUID.randomUUID()).nombre("Ana").email("a@h.com").passwordHash("h").build();
    private Plan pichanga;

    private LlegadaService a(int hora, int minuto) {
        Instant ahora = LocalDateTime.of(SABADO, LocalTime.of(hora, minuto)).atZone(ZonaHoraria.ZONA).toInstant();
        return new LlegadaService(planes, llegadas, miembros, usuarios, eventos, Clock.fixed(ahora, ZoneOffset.UTC));
    }

    @BeforeEach
    void datos() {
        VentanaPlan ventana = VentanaPlan.builder().id(UUID.randomUUID()).fecha(SABADO)
                .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(20, 0)).build();
        pichanga = Plan.builder().id(UUID.randomUUID()).grupo(grupo).titulo("Pichanga").estado(Plan.Estado.CONFIRMADO)
                .ventanaConfirmada(ventana).build();
        when(planes.findByIdConVentanas(pichanga.getId())).thenReturn(Optional.of(pichanga));
        when(miembros.existsByGrupo_IdAndUsuario_Id(grupo.getId(), ana.getId())).thenReturn(true);
        when(usuarios.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(usuarios.findAllById(any())).thenReturn(List.of(ana));
        when(llegadas.delPlan(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Se puede marcar desde media hora antes hasta una hora después de que termine")
    void ventana() {
        assertThat(a(17, 29).ver(ana.getId(), pichanga.getId()).estado()).isEqualTo(LlegadasResponse.Estado.ANTES);
        assertThat(a(17, 30).ver(ana.getId(), pichanga.getId()).estado()).isEqualTo(LlegadasResponse.Estado.ABIERTA);
        assertThat(a(21, 0).ver(ana.getId(), pichanga.getId()).estado()).isEqualTo(LlegadasResponse.Estado.ABIERTA);
        assertThat(a(21, 1).ver(ana.getId(), pichanga.getId()).estado()).isEqualTo(LlegadasResponse.Estado.CERRADA);

        assertThatThrownBy(() -> a(17, 0).marcar(ana.getId(), pichanga.getId())).isInstanceOf(BusinessException.class);
        verify(llegadas, never()).save(any());
    }

    @Test
    @DisplayName("Marcar guarda la hora y avisa al grupo; hasta 10 minutos tarde cuenta como a tiempo")
    void marcar() {
        a(18, 10).marcar(ana.getId(), pichanga.getId());

        ArgumentCaptor<LlegadaRegistrada> evento = ArgumentCaptor.forClass(LlegadaRegistrada.class);
        verify(llegadas).save(any(Llegada.class));
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().aTiempo()).isTrue();
        assertThat(evento.getValue().nombre()).isEqualTo("Ana");

        Instant inicio = LocalDateTime.of(SABADO, LocalTime.of(18, 0)).atZone(ZonaHoraria.ZONA).toInstant();
        assertThat(LlegadaService.aTiempo(inicio.plusSeconds(11 * 60), inicio)).isFalse();
    }

    @Test
    @DisplayName("Marcar dos veces no cambia la primera hora ni vuelve a avisar")
    void unaVez() {
        when(llegadas.existsById(new Llegada.Clave(pichanga.getId(), ana.getId()))).thenReturn(true);

        a(18, 30).marcar(ana.getId(), pichanga.getId());

        verify(llegadas, never()).save(any());
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Un plan sin confirmar no tiene «Llegué», y fuera del grupo el plan no existe")
    void sinFechaYAjenos() {
        pichanga.setEstado(Plan.Estado.PROPUESTO);
        assertThat(a(18, 0).ver(ana.getId(), pichanga.getId()).estado()).isEqualTo(LlegadasResponse.Estado.SIN_FECHA);

        UUID intrusa = UUID.randomUUID();
        assertThatThrownBy(() -> a(18, 0).ver(intrusa, pichanga.getId())).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("La respuesta dice quiénes llegaron y si yo ya marqué")
    void quienesLlegaron() {
        Instant llego = LocalDateTime.of(SABADO, LocalTime.of(18, 5)).atZone(ZonaHoraria.ZONA).toInstant();
        when(llegadas.delPlan(pichanga.getId())).thenReturn(List.of(new Llegada(new Llegada.Clave(pichanga.getId(), ana.getId()), llego)));

        LlegadasResponse r = a(19, 0).ver(ana.getId(), pichanga.getId());

        assertThat(r.yaLlegue()).isTrue();
        assertThat(r.llegadas()).extracting(LlegadasResponse.Persona::nombre).containsExactly("Ana");
        assertThat(r.llegadas().get(0).aTiempo()).isTrue();
    }
}
