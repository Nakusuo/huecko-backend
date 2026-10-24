package com.huecko.backend.libre.service;

import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.libre.dto.LibreAhoraResponse;
import com.huecko.backend.libre.dto.MarcarLibreRequest;
import com.huecko.backend.mongo.document.BloqueHorario;
import com.huecko.backend.mongo.repository.BloqueHorarioRepository;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.GrupoRepository;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.tiemporeal.LibreAnunciado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * «¿Quién está libre ahora?»: nombres solo de quien avisa, del resto un
 * recuento (RNF-02), y tu horario para sugerirte hasta cuándo.
 */
class LibreAhoraServiceTest {

    /** Jueves 22 de octubre de 2026, 11:30 en Lima. */
    private static final LocalDateTime JUEVES = LocalDateTime.of(2026, 10, 22, 11, 30);
    private static final Instant AHORA = JUEVES.atZone(ZonaHoraria.ZONA).toInstant();

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final GrupoRepository grupos = mock(GrupoRepository.class);
    private final MiembroGrupoRepository miembros = mock(MiembroGrupoRepository.class);
    private final BloqueHorarioRepository bloques = mock(BloqueHorarioRepository.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final LibreAhoraService servicio = new LibreAhoraService(usuarios, grupos, miembros, bloques, eventos,
            Clock.fixed(AHORA, ZoneOffset.UTC));

    private final Grupo proyecto = Grupo.builder().id(UUID.randomUUID()).nombre("Proyecto").build();
    private Usuario ana;
    private Usuario beto;
    private Usuario carla;
    private final List<BloqueHorario> horario = new ArrayList<>();

    private static Usuario persona(String nombre) {
        return Usuario.builder().id(UUID.randomUUID()).nombre(nombre).email(nombre + "@h.com").passwordHash("h").build();
    }

    private static BloqueHorario clase(Usuario quien, int dia, String inicio, String fin) {
        return BloqueHorario.builder().usuarioId(quien.getId().toString()).tipo(BloqueHorario.Tipo.RECURRENTE)
                .diaSemana(dia).horaInicio(LocalTime.parse(inicio)).horaFin(LocalTime.parse(fin))
                .etiqueta("Terapia").estado(BloqueHorario.Estado.CONFIRMADO).build();
    }

    @BeforeEach
    void grupo() {
        ana = persona("Ana");
        beto = persona("Beto");
        carla = persona("Carla");
        for (Usuario u : List.of(ana, beto, carla)) {
            when(usuarios.findById(u.getId())).thenReturn(Optional.of(u));
        }
        when(usuarios.save(any())).thenAnswer(i -> i.getArgument(0));
        when(grupos.findByMiembro(any())).thenReturn(List.of(proyecto));
        when(miembros.findByGrupoIdConUsuario(proyecto.getId())).thenReturn(List.of(
                MiembroGrupo.builder().grupo(proyecto).usuario(ana).build(),
                MiembroGrupo.builder().grupo(proyecto).usuario(beto).build(),
                MiembroGrupo.builder().grupo(proyecto).usuario(carla).build()));
        when(bloques.findByUsuarioIdInAndEstado(anyList(), eq(BloqueHorario.Estado.CONFIRMADO))).thenReturn(horario);
        when(bloques.findByUsuarioIdAndEstado(any(), eq(BloqueHorario.Estado.CONFIRMADO)))
                .thenAnswer(i -> horario.stream().filter(b -> b.getUsuarioId().equals(i.getArgument(0))).toList());
    }

    @Test
    @DisplayName("Del horario de los demás solo sale cuántos no tienen clase, nunca quiénes")
    void soloRecuento() {
        horario.add(clase(beto, 4, "10:00", "12:00"));   // Beto está en clase
        horario.add(clase(carla, 4, "14:00", "16:00"));  // Carla tiene clase luego

        LibreAhoraResponse estado = servicio.estado(ana.getId());

        LibreAhoraResponse.GrupoLibre grupo = estado.grupos().get(0);
        assertThat(grupo.sinClase()).isEqualTo(2);
        assertThat(grupo.total()).isEqualTo(3);
        // Carla no tiene clase, pero no avisó: no sale su nombre.
        assertThat(grupo.personas()).isEmpty();
    }

    @Test
    @DisplayName("Con nombre solo sale quien avisó y aún no se le pasó la hora; nunca una misma")
    void soloQuienAvisa() {
        beto.setLibreHasta(AHORA.plus(Duration.ofHours(2)));
        beto.setLibreNota("En la biblioteca");
        carla.setLibreHasta(AHORA.minus(Duration.ofMinutes(1)));
        ana.setLibreHasta(AHORA.plus(Duration.ofHours(1)));

        LibreAhoraResponse estado = servicio.estado(ana.getId());

        assertThat(estado.grupos().get(0).personas())
                .extracting(LibreAhoraResponse.PersonaLibre::nombre, LibreAhoraResponse.PersonaLibre::nota)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Beto", "En la biblioteca"));
        assertThat(estado.yo().hasta()).isEqualTo(AHORA.plus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("Tu horario sugiere hasta tu siguiente clase de hoy, o hasta las 22:00 si no te queda ninguna")
    void sugerencia() {
        horario.add(clase(ana, 4, "14:00", "16:00"));
        horario.add(clase(ana, 4, "17:00", "18:00"));
        horario.add(clase(ana, 5, "12:00", "13:00")); // viernes: no cuenta hoy

        assertThat(servicio.estado(ana.getId()).sugerencia())
                .isEqualTo(JUEVES.toLocalDate().atTime(14, 0).atZone(ZonaHoraria.ZONA).toInstant());

        horario.clear();
        assertThat(servicio.estado(ana.getId()).sugerencia())
                .isEqualTo(JUEVES.toLocalDate().atTime(22, 0).atZone(ZonaHoraria.ZONA).toInstant());
    }

    @Test
    @DisplayName("De noche el horario no sugiere nada, y avisar sin hora vale una hora")
    void deNoche() {
        Instant noche = JUEVES.withHour(22).withMinute(51).atZone(ZonaHoraria.ZONA).toInstant();
        LibreAhoraService tarde = new LibreAhoraService(usuarios, grupos, miembros, bloques, eventos,
                Clock.fixed(noche, ZoneOffset.UTC));

        assertThat(tarde.estado(ana.getId()).sugerencia()).isNull();
        assertThat(tarde.marcar(ana.getId(), new MarcarLibreRequest(null, null)).yo().hasta())
                .isEqualTo(noche.plus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("En clase no hay sugerencia, pero se puede avisar igual: quien sabe si está libre es la persona")
    void enClase() {
        horario.add(clase(ana, 4, "11:00", "13:00"));

        LibreAhoraResponse estado = servicio.estado(ana.getId());
        assertThat(estado.enClase()).isTrue();
        assertThat(estado.sugerencia()).isNull();

        LibreAhoraResponse avisado = servicio.marcar(ana.getId(), new MarcarLibreRequest(null, null));
        assertThat(avisado.yo().hasta()).isEqualTo(AHORA.plus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("Un bloque puntual cuenta los días entre su fecha y su fecha de fin")
    void puntual() {
        BloqueHorario viaje = BloqueHorario.builder().usuarioId(beto.getId().toString()).tipo(BloqueHorario.Tipo.PUNTUAL)
                .fecha(LocalDate.of(2026, 10, 21)).fechaFin(LocalDate.of(2026, 10, 23))
                .horaInicio(LocalTime.of(8, 0)).horaFin(LocalTime.of(20, 0)).estado(BloqueHorario.Estado.CONFIRMADO).build();

        assertThat(HorarioDeHoy.ocupadoA(List.of(viaje), JUEVES)).isTrue();
        assertThat(HorarioDeHoy.ocupadoA(List.of(viaje), JUEVES.plusDays(2))).isFalse();
        assertThat(HorarioDeHoy.ocupadoA(List.of(viaje), JUEVES.withHour(20).withMinute(0))).isFalse();
    }

    @Test
    @DisplayName("Avisar llega en vivo a tus grupos, con la hora y la nota que escribiste")
    void avisoEnVivo() {
        Instant hasta = AHORA.plus(Duration.ofHours(2));

        servicio.marcar(ana.getId(), new MarcarLibreRequest(hasta, "  En la   cafetería "));

        ArgumentCaptor<LibreAnunciado> evento = ArgumentCaptor.forClass(LibreAnunciado.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().grupoIds()).containsExactly(proyecto.getId());
        assertThat(evento.getValue().nombre()).isEqualTo("Ana");
        assertThat(evento.getValue().hasta()).isEqualTo(hasta);
        assertThat(evento.getValue().nota()).isEqualTo("En la cafetería");
    }

    @Test
    @DisplayName("Alargar el aviso no vuelve a avisar, y hay un tope de avisos por hora")
    void sinInundar() {
        servicio.marcar(ana.getId(), new MarcarLibreRequest(AHORA.plus(Duration.ofHours(1)), null));
        servicio.marcar(ana.getId(), new MarcarLibreRequest(AHORA.plus(Duration.ofHours(2)), null));
        verify(eventos, times(1)).publishEvent(any(LibreAnunciado.class));

        for (int i = 1; i < LibreAhoraService.MAX_AVISOS + 2; i++) {
            servicio.quitar(ana.getId());
            servicio.marcar(ana.getId(), new MarcarLibreRequest(AHORA.plus(Duration.ofHours(1)), null));
        }
        verify(eventos, times(LibreAhoraService.MAX_AVISOS)).publishEvent(any(LibreAnunciado.class));
    }

    @Test
    @DisplayName("La hora tiene que ser de aquí a un rato y como mucho 12 horas")
    void horaValida() {
        assertThatThrownBy(() -> servicio.marcar(ana.getId(), new MarcarLibreRequest(AHORA.plus(Duration.ofMinutes(5)), null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> servicio.marcar(ana.getId(), new MarcarLibreRequest(AHORA.plus(Duration.ofHours(13)), null)))
                .isInstanceOf(BusinessException.class);
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("Quitar el aviso lo borra del todo")
    void quitar() {
        ana.setLibreHasta(AHORA.plus(Duration.ofHours(1)));
        ana.setLibreNota("Por aquí");

        LibreAhoraResponse estado = servicio.quitar(ana.getId());

        assertThat(estado.yo()).isNull();
        assertThat(ana.getLibreNota()).isNull();
    }
}
