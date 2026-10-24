package com.huecko.backend.recordatorio;

import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.VentanaPlan;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.tiemporeal.Recordatorio;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Recordatorios: una hora antes del plan y del cierre de la votación, una sola vez. */
class RecordatoriosServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 24);
    private static final Instant AHORA = LocalDateTime.of(HOY, LocalTime.of(17, 10)).atZone(ZonaHoraria.ZONA).toInstant();

    private final PlanRepository planes = mock(PlanRepository.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final RecordatoriosService servicio = new RecordatoriosService(planes, eventos, Clock.fixed(AHORA, ZoneOffset.UTC));
    private final Grupo grupo = Grupo.builder().id(UUID.randomUUID()).nombre("Proyecto").build();

    private Plan confirmado(String titulo, LocalTime inicio) {
        VentanaPlan v = VentanaPlan.builder().id(UUID.randomUUID()).fecha(HOY).horaInicio(inicio).horaFin(inicio.plusHours(2)).build();
        return Plan.builder().id(UUID.randomUUID()).grupo(grupo).titulo(titulo).lugar("Losa").estado(Plan.Estado.CONFIRMADO)
                .ventanaConfirmada(v).build();
    }

    @Test
    @DisplayName("Avisa del plan que empieza dentro de una hora y lo marca; el de más tarde aún no")
    void empiezaPronto() {
        Plan pronto = confirmado("Pichanga", LocalTime.of(18, 0));
        Plan tarde = confirmado("Cena", LocalTime.of(21, 0));
        Plan yaEmpezo = confirmado("Estudio", LocalTime.of(16, 0));
        when(planes.confirmadosSinRecordatorio(HOY, HOY.plusDays(1))).thenReturn(List.of(pronto, tarde, yaEmpezo));
        when(planes.votacionesPorCerrarSinAviso(any(), any())).thenReturn(List.of());

        int enviados = servicio.enviarPendientes();

        assertThat(enviados).isEqualTo(1);
        ArgumentCaptor<Recordatorio> evento = ArgumentCaptor.forClass(Recordatorio.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo(Recordatorio.Tipo.EMPIEZA_PRONTO);
        assertThat(evento.getValue().titulo()).isEqualTo("Pichanga");
        assertThat(pronto.getRecordatorioEnviadoEn()).isEqualTo(AHORA);
        assertThat(tarde.getRecordatorioEnviadoEn()).isNull();
        assertThat(yaEmpezo.getRecordatorioEnviadoEn()).isNull();
    }

    @Test
    @DisplayName("Avisa de las votaciones que cierran en la próxima hora y las marca")
    void cierraPronto() {
        Plan votacion = Plan.builder().id(UUID.randomUUID()).grupo(grupo).titulo("Cine").estado(Plan.Estado.PROPUESTO)
                .plazoVotacion(AHORA.plusSeconds(40 * 60)).build();
        when(planes.confirmadosSinRecordatorio(any(), any())).thenReturn(List.of());
        when(planes.votacionesPorCerrarSinAviso(AHORA, AHORA.plusSeconds(3600))).thenReturn(List.of(votacion));

        assertThat(servicio.enviarPendientes()).isEqualTo(1);

        ArgumentCaptor<Recordatorio> evento = ArgumentCaptor.forClass(Recordatorio.class);
        verify(eventos, times(1)).publishEvent(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo(Recordatorio.Tipo.CIERRA_PRONTO);
        assertThat(evento.getValue().cuando()).isEqualTo(votacion.getPlazoVotacion());
        assertThat(votacion.getAvisoCierreEnviadoEn()).isEqualTo(AHORA);
    }
}
