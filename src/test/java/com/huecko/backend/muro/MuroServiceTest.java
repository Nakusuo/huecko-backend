package com.huecko.backend.muro;

import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.muro.MuroResponse.Entrada;
import com.huecko.backend.muro.MuroResponse.Tipo;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.Llegada;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.entity.VentanaPlan;
import com.huecko.backend.postgres.repository.FotoPlanRepository;
import com.huecko.backend.postgres.repository.FotoPlanRepository.FotoPlanResumen;
import com.huecko.backend.postgres.repository.LlegadaRepository;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El muro sale de los datos de siempre: planes, fotos, llegadas y quién entró. */
class MuroServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-20T15:00:00Z");

    private final MiembroGrupoRepository miembros = mock(MiembroGrupoRepository.class);
    private final PlanRepository planes = mock(PlanRepository.class);
    private final FotoPlanRepository fotos = mock(FotoPlanRepository.class);
    private final LlegadaRepository llegadas = mock(LlegadaRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final MuroService servicio = new MuroService(miembros, planes, fotos, llegadas, usuarios);

    private final Grupo grupo = Grupo.builder().id(UUID.randomUUID()).nombre("Proyecto").build();
    private final Usuario ana = Usuario.builder().id(UUID.randomUUID()).nombre("Ana").email("a@h.com").passwordHash("h").build();
    private final Usuario beto = Usuario.builder().id(UUID.randomUUID()).nombre("Beto").email("b@h.com").passwordHash("h").build();
    private Plan pichanga;

    private static Instant mas(int minutos) {
        return T0.plus(Duration.ofMinutes(minutos));
    }

    private FotoPlanResumen foto(Usuario autor, int minuto) {
        FotoPlanResumen f = mock(FotoPlanResumen.class);
        UUID id = UUID.randomUUID();
        when(f.getId()).thenReturn(id);
        when(f.getPlanId()).thenReturn(pichanga.getId());
        when(f.getGrupoId()).thenReturn(grupo.getId());
        when(f.getAutorId()).thenReturn(autor.getId());
        when(f.getSubidaEn()).thenReturn(mas(minuto));
        return f;
    }

    @BeforeEach
    void datos() {
        when(miembros.existsByGrupo_IdAndUsuario_Id(grupo.getId(), ana.getId())).thenReturn(true);
        when(miembros.findByGrupoIdConUsuario(grupo.getId())).thenReturn(List.of(
                MiembroGrupo.builder().grupo(grupo).usuario(ana).build(),               // de antes: sin fecha
                MiembroGrupo.builder().grupo(grupo).usuario(beto).unidoEn(mas(5)).build()));
        VentanaPlan ventana = VentanaPlan.builder().id(UUID.randomUUID()).fecha(LocalDate.of(2026, 10, 24))
                .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(20, 0)).build();
        pichanga = Plan.builder().id(UUID.randomUUID()).grupo(grupo).titulo("Pichanga").creadoPor(ana)
                .estado(Plan.Estado.CONFIRMADO).ventanaConfirmada(ventana).creadoEn(mas(10)).cerradoEn(mas(60)).build();
        Plan cine = Plan.builder().id(UUID.randomUUID()).grupo(grupo).titulo("Cine").creadoPor(beto)
                .estado(Plan.Estado.CANCELADO).creadoEn(mas(20)).cerradoEn(mas(70)).build();
        when(planes.findByGrupoConVentanas(grupo.getId())).thenReturn(List.of(pichanga, cine));
        when(llegadas.deLosPlanes(anyCollection())).thenReturn(List.of(
                new Llegada(new Llegada.Clave(pichanga.getId(), beto.getId()), mas(200)),
                new Llegada(new Llegada.Clave(pichanga.getId(), ana.getId()), mas(195))));
        when(usuarios.findAllById(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Junta todo lo que pasó, de lo más nuevo a lo más antiguo")
    void orden() {
        List<FotoPlanResumen> unaFoto = List.of(foto(ana, 300));
        when(fotos.findByGrupoIdOrderBySubidaEnAsc(grupo.getId())).thenReturn(unaFoto);

        List<Entrada> muro = servicio.muro(ana.getId(), grupo.getId(), null).entradas();

        assertThat(muro).extracting(Entrada::tipo).containsExactly(
                Tipo.FOTOS_SUBIDAS, Tipo.LLEGARON, Tipo.PLAN_CANCELADO, Tipo.PLAN_CONFIRMADO,
                Tipo.PLAN_PROPUESTO, Tipo.PLAN_PROPUESTO, Tipo.MIEMBRO_UNIDO);
        assertThat(muro.get(3).fecha()).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(muro.get(6).actor().nombre()).isEqualTo("Beto");
    }

    @Test
    @DisplayName("Las fotos seguidas de una persona son una sola entrada; con más de 10 minutos de por medio, dos")
    void tandas() {
        List<FotoPlanResumen> varias = List.of(
                foto(ana, 300), foto(ana, 302), foto(ana, 305), foto(beto, 303), foto(ana, 330));
        when(fotos.findByGrupoIdOrderBySubidaEnAsc(grupo.getId())).thenReturn(varias);

        List<Entrada> deFotos = servicio.muro(ana.getId(), grupo.getId(), null).entradas().stream()
                .filter(e -> e.tipo() == Tipo.FOTOS_SUBIDAS).toList();

        assertThat(deFotos).extracting(e -> e.actor().nombre(), Entrada::cantidad).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Ana", 1),
                org.assertj.core.groups.Tuple.tuple("Ana", 3),
                org.assertj.core.groups.Tuple.tuple("Beto", 1));
        assertThat(deFotos.get(1).fotoIds()).hasSize(3);
        assertThat(deFotos.get(1).titulo()).isEqualTo("Pichanga");
    }

    @Test
    @DisplayName("Las llegadas de un plan van juntas, en el orden en que llegaron")
    void llegadas() {
        when(fotos.findByGrupoIdOrderBySubidaEnAsc(grupo.getId())).thenReturn(List.of());

        Entrada llegaron = servicio.muro(ana.getId(), grupo.getId(), null).entradas().stream()
                .filter(e -> e.tipo() == Tipo.LLEGARON).findFirst().orElseThrow();

        assertThat(llegaron.personas()).extracting(MuroResponse.Persona::nombre).containsExactly("Ana", "Beto");
        assertThat(llegaron.ocurridoEn()).isEqualTo(mas(200));
    }

    @Test
    @DisplayName("Va por páginas: «antes» trae lo anterior a la última entrada vista")
    void paginas() {
        List<FotoPlanResumen> muchas = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            muchas.add(foto(i % 2 == 0 ? ana : beto, 1000 + i * 20));
        }
        when(fotos.findByGrupoIdOrderBySubidaEnAsc(grupo.getId())).thenReturn(muchas);

        MuroResponse primera = servicio.muro(ana.getId(), grupo.getId(), null);
        assertThat(primera.entradas()).hasSize(MuroService.POR_PAGINA);
        assertThat(primera.siguiente()).isNotNull();

        MuroResponse segunda = servicio.muro(ana.getId(), grupo.getId(), primera.siguiente());
        assertThat(segunda.entradas()).allMatch(e -> e.ocurridoEn().isBefore(primera.siguiente()));
        assertThat(segunda.siguiente()).isNull();
    }

    @Test
    @DisplayName("Fuera del grupo no hay muro")
    void soloElGrupo() {
        assertThatThrownBy(() -> servicio.muro(UUID.randomUUID(), grupo.getId(), null)).isInstanceOf(NotFoundException.class);
    }
}
