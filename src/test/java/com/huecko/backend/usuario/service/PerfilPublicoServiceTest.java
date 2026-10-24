package com.huecko.backend.usuario.service;

import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.mongo.document.AlertaRetraso;
import com.huecko.backend.mongo.document.Ausencia;
import com.huecko.backend.mongo.repository.AlertaRetrasoRepository;
import com.huecko.backend.mongo.repository.AusenciaRepository;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.Llegada;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.entity.VentanaPlan;
import com.huecko.backend.postgres.repository.GrupoRepository;
import com.huecko.backend.postgres.repository.LlegadaRepository;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.usuario.dto.LogrosResponse;
import com.huecko.backend.usuario.dto.PerfilPublicoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La tarjeta de perfil y los logros salen de los planes, retrasos y ausencias que ya guarda Huecko. */
class PerfilPublicoServiceTest {

    private static final Instant AHORA =
            LocalDateTime.of(2026, 10, 23, 12, 0).atZone(ZonaHoraria.ZONA).toInstant();

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final GrupoRepository grupos = mock(GrupoRepository.class);
    private final MiembroGrupoRepository miembros = mock(MiembroGrupoRepository.class);
    private final PlanRepository planes = mock(PlanRepository.class);
    private final AusenciaRepository ausencias = mock(AusenciaRepository.class);
    private final AlertaRetrasoRepository retrasos = mock(AlertaRetrasoRepository.class);
    private final LlegadaRepository llegadas = mock(LlegadaRepository.class);
    private final PerfilPublicoService servicio = new PerfilPublicoService(usuarios, grupos, miembros, planes,
            ausencias, retrasos, llegadas, Clock.fixed(AHORA, ZoneOffset.UTC));

    private final Grupo proyecto = Grupo.builder().id(UUID.randomUUID()).nombre("Proyecto").build();
    private Usuario ana;
    private final UUID beto = UUID.randomUUID();

    private final Plan pasadoATiempo = confirmado(LocalDate.of(2026, 10, 20));
    private final Plan pasadoConRetraso = confirmado(LocalDate.of(2026, 10, 21));
    private final Plan pasadoAusente = confirmado(LocalDate.of(2026, 10, 22));
    private final Plan futuro = confirmado(LocalDate.of(2026, 10, 25));

    private static Plan confirmado(LocalDate fecha) {
        VentanaPlan ventana = VentanaPlan.builder().id(UUID.randomUUID()).fecha(fecha)
                .horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(12, 0)).build();
        return Plan.builder().id(UUID.randomUUID()).estado(Plan.Estado.CONFIRMADO).ventanaConfirmada(ventana).build();
    }

    @BeforeEach
    void datos() {
        ana = Usuario.builder().id(UUID.randomUUID()).nombre("Ana").email("ana@h.com").passwordHash("h")
                .carrera("Ingeniería de Software").ciclo(6).bio("Siempre con café")
                .intereses(new java.util.ArrayList<>(List.of("Fútbol", "Anime"))).creadoEn(AHORA).build();
        when(usuarios.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(grupos.findByMiembro(ana.getId())).thenReturn(List.of(proyecto));
        when(planes.findConfirmadosConFecha(anyCollection()))
                .thenReturn(List.of(pasadoATiempo, pasadoConRetraso, pasadoAusente, futuro));
        when(ausencias.findByUsuarioId(ana.getId().toString())).thenReturn(List.of(
                Ausencia.builder().planId(pasadoAusente.getId().toString()).usuarioId(ana.getId().toString()).build()));
        when(retrasos.findByUsuarioId(ana.getId().toString())).thenReturn(List.of(
                AlertaRetraso.builder().planId(pasadoConRetraso.getId().toString()).usuarioId(ana.getId().toString()).build()));
        when(planes.countByCreadoPor_IdAndEstadoNot(ana.getId(), Plan.Estado.CANCELADO)).thenReturn(3L);
    }

    @Test
    @DisplayName("Cuenta los planes que ya empezaron, sin los que avisó que faltaría ni los futuros")
    void logros() {
        LogrosResponse logros = servicio.logros(ana.getId());

        assertThat(logros.planesVividos()).isEqualTo(2);
        assertThat(logros.aTiempo()).isEqualTo(1);
        assertThat(logros.propuestos()).isEqualTo(3);
        assertThat(logros.insignias()).containsExactly("PRIMER_PLAN", "ORGANIZA");
    }

    @Test
    @DisplayName("Insignias: primer plan, 5 planes, puntual con el 90 % a tiempo y 3 propuestas")
    void reglasDeInsignias() {
        assertThat(PerfilPublicoService.insignias(0, 0, 0)).isEmpty();
        assertThat(PerfilPublicoService.insignias(1, 0, 0)).containsExactly("PRIMER_PLAN");
        assertThat(PerfilPublicoService.insignias(3, 3, 0)).containsExactly("PRIMER_PLAN", "PUNTUAL");
        assertThat(PerfilPublicoService.insignias(3, 2, 0)).containsExactly("PRIMER_PLAN");
        assertThat(PerfilPublicoService.insignias(10, 9, 2)).containsExactly("PRIMER_PLAN", "HABITUAL", "PUNTUAL");
        assertThat(PerfilPublicoService.insignias(10, 8, 3)).containsExactly("PRIMER_PLAN", "HABITUAL", "ORGANIZA");
    }

    @Test
    @DisplayName("Sin un grupo en común la tarjeta no existe, y no se llega a buscar a la persona")
    void sinGrupoEnComun() {
        when(miembros.gruposEnComun(beto, ana.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.perfil(beto, ana.getId())).isInstanceOf(NotFoundException.class);
        verify(usuarios, never()).findById(any());
    }

    @Test
    @DisplayName("Con un grupo en común se ve su perfil, los grupos compartidos y los planes a los que fueron juntos")
    void tarjetaAjena() {
        when(miembros.gruposEnComun(beto, ana.getId())).thenReturn(List.of(proyecto));
        when(grupos.findByMiembro(beto)).thenReturn(List.of(proyecto));
        // Beto no avisó de nada: vivió los tres pasados; con Ana coinciden dos.
        when(ausencias.findByUsuarioId(beto.toString())).thenReturn(List.of());
        when(retrasos.findByUsuarioId(beto.toString())).thenReturn(List.of());

        PerfilPublicoResponse tarjeta = servicio.perfil(beto, ana.getId());

        assertThat(tarjeta.nombre()).isEqualTo("Ana");
        assertThat(tarjeta.carrera()).isEqualTo("Ingeniería de Software");
        assertThat(tarjeta.intereses()).containsExactly("Fútbol", "Anime");
        assertThat(tarjeta.gruposEnComun()).containsExactly("Proyecto");
        assertThat(tarjeta.planesJuntos()).isEqualTo(2);
        assertThat(tarjeta.insignias()).containsExactly("PRIMER_PLAN", "ORGANIZA");
    }

    @Test
    @DisplayName("Tu propia tarjeta muestra todos tus grupos y no cuenta planes «juntos»")
    void tarjetaPropia() {
        PerfilPublicoResponse tarjeta = servicio.perfil(ana.getId(), ana.getId());

        assertThat(tarjeta.gruposEnComun()).containsExactly("Proyecto");
        assertThat(tarjeta.planesJuntos()).isNull();
        verify(miembros, never()).gruposEnComun(any(), any());
    }

    @Test
    @DisplayName("Si en un plan se usó «Llegué», cuenta lo marcado: sin marcarlo no fuiste, y tarde no es a tiempo")
    void conLlegadas() {
        Instant inicioATiempo = LocalDateTime.of(pasadoATiempo.getVentanaConfirmada().getFecha(), LocalTime.of(10, 0))
                .atZone(ZonaHoraria.ZONA).toInstant();
        Instant inicioAusente = LocalDateTime.of(pasadoAusente.getVentanaConfirmada().getFecha(), LocalTime.of(10, 0))
                .atZone(ZonaHoraria.ZONA).toInstant();
        UUID otra = UUID.randomUUID();
        when(llegadas.deLosPlanes(anyCollection())).thenReturn(List.of(
                // En el que «iba a tiempo» solo marcó otra persona: Ana no fue.
                new Llegada(new Llegada.Clave(pasadoATiempo.getId(), otra), inicioATiempo),
                // En el que avisó que faltaría sí marcó, 25 minutos tarde: fue, pero no a tiempo.
                new Llegada(new Llegada.Clave(pasadoAusente.getId(), ana.getId()), inicioAusente.plusSeconds(25 * 60))));

        LogrosResponse logros = servicio.logros(ana.getId());

        // pasadoConRetraso no tiene llegadas: sigue la aproximación (fue, con retraso).
        assertThat(logros.planesVividos()).isEqualTo(2);
        assertThat(logros.aTiempo()).isZero();
    }
}
