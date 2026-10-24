package com.huecko.backend.album.service;

import com.huecko.backend.album.dto.AlbumResponses;
import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.ForbiddenException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.postgres.entity.FotoPlan;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.entity.VentanaPlan;
import com.huecko.backend.postgres.repository.FotoPlanRepository;
import com.huecko.backend.postgres.repository.FotoPlanRepository.FotoPlanResumen;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.tiemporeal.AlbumCambiado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El álbum: fotos de planes que ya empezaron, solo para el grupo y con topes. */
class AlbumServiceTest {

    private static final Instant AHORA = LocalDateTime.of(2026, 10, 24, 20, 0).atZone(ZonaHoraria.ZONA).toInstant();

    private final FotoPlanRepository fotos = mock(FotoPlanRepository.class);
    private final PlanRepository planes = mock(PlanRepository.class);
    private final MiembroGrupoRepository miembros = mock(MiembroGrupoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final ConversacionFotoService conversacion = mock(ConversacionFotoService.class);
    private final AlbumService servicio = new AlbumService(fotos, planes, miembros, usuarios, conversacion, eventos,
            Clock.fixed(AHORA, ZoneOffset.UTC));

    private final Grupo grupo = Grupo.builder().id(UUID.randomUUID()).nombre("Proyecto").build();
    private final Usuario ana = Usuario.builder().id(UUID.randomUUID()).nombre("Ana").email("a@h.com").passwordHash("h").build();
    private final Usuario beto = Usuario.builder().id(UUID.randomUUID()).nombre("Beto").email("b@h.com").passwordHash("h").build();
    private Plan pichanga;

    private Plan plan(String titulo, LocalDate fecha, Plan.Estado estado) {
        VentanaPlan ventana = VentanaPlan.builder().id(UUID.randomUUID()).fecha(fecha)
                .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(20, 0)).build();
        Plan plan = Plan.builder().id(UUID.randomUUID()).grupo(grupo).titulo(titulo).estado(estado)
                .ventanaConfirmada(estado == Plan.Estado.CONFIRMADO ? ventana : null).build();
        when(planes.findByIdConVentanas(plan.getId())).thenReturn(Optional.of(plan));
        return plan;
    }

    private void miembro(Usuario u, MiembroGrupo.Rol rol) {
        when(miembros.findByGrupo_IdAndUsuario_Id(grupo.getId(), u.getId()))
                .thenReturn(Optional.of(MiembroGrupo.builder().grupo(grupo).usuario(u).rol(rol).build()));
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB), "png", salida);
        return salida.toByteArray();
    }

    private static FotoPlanResumen resumen(UUID id, UUID plan, UUID grupo, UUID autor) {
        FotoPlanResumen r = mock(FotoPlanResumen.class);
        when(r.getId()).thenReturn(id);
        when(r.getPlanId()).thenReturn(plan);
        when(r.getGrupoId()).thenReturn(grupo);
        when(r.getAutorId()).thenReturn(autor);
        when(r.getSubidaEn()).thenReturn(AHORA);
        return r;
    }

    @BeforeEach
    void datos() {
        miembro(ana, MiembroGrupo.Rol.MIEMBRO);
        when(usuarios.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(usuarios.findAllById(any())).thenReturn(List.of(ana, beto));
        pichanga = plan("Pichanga", LocalDate.of(2026, 10, 24), Plan.Estado.CONFIRMADO); // empezó a las 18:00
    }

    @Test
    @DisplayName("Las fotos se suben cuando el plan confirmado ya empezó, no antes")
    void soloDespuesDeEmpezar() {
        Plan futuro = plan("Cena", LocalDate.of(2026, 10, 30), Plan.Estado.CONFIRMADO);
        Plan enVotacion = plan("Cine", LocalDate.of(2026, 10, 20), Plan.Estado.PROPUESTO);

        assertThatThrownBy(() -> servicio.subir(ana.getId(), futuro.getId(), List.of(png())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ya empezó");
        assertThatThrownBy(() -> servicio.subir(ana.getId(), enVotacion.getId(), List.of(png())))
                .isInstanceOf(BusinessException.class);
        verify(fotos, never()).save(any());
    }

    @Test
    @DisplayName("Subir guarda cada foto rehecha y avisa al grupo de cuántas fueron")
    void subir() throws IOException {
        ArgumentCaptor<FotoPlan> guardadas = ArgumentCaptor.forClass(FotoPlan.class);

        servicio.subir(ana.getId(), pichanga.getId(), List.of(png(), png()));

        verify(fotos, times(2)).save(guardadas.capture());
        FotoPlan primera = guardadas.getAllValues().get(0);
        assertThat(primera.getGrupoId()).isEqualTo(grupo.getId());
        assertThat(primera.getAutorId()).isEqualTo(ana.getId());
        assertThat(primera.getAncho()).isEqualTo(800);
        assertThat(primera.getMiniatura()).isNotEmpty();
        assertThat(guardadas.getAllValues().get(1).getSubidaEn()).isAfter(primera.getSubidaEn());

        ArgumentCaptor<AlbumCambiado> evento = ArgumentCaptor.forClass(AlbumCambiado.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().subidas()).isEqualTo(2);
        assertThat(evento.getValue().nombre()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("Diez por persona en cada plan y sesenta por grupo")
    void topes() {
        when(fotos.countByPlanIdAndAutorId(pichanga.getId(), ana.getId())).thenReturn(9L);
        assertThatThrownBy(() -> servicio.subir(ana.getId(), pichanga.getId(), List.of(png(), png())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("te quedan 1");

        when(fotos.countByPlanIdAndAutorId(pichanga.getId(), ana.getId())).thenReturn(0L);
        when(fotos.countByGrupoId(grupo.getId())).thenReturn(59L);
        assertThatThrownBy(() -> servicio.subir(ana.getId(), pichanga.getId(), List.of(png(), png())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("quedan 1 de 60");
        verify(fotos, never()).save(any());
    }

    @Test
    @DisplayName("Si una de las fotos no es una imagen, no se guarda ninguna")
    void todasONinguna() {
        assertThatThrownBy(() -> servicio.subir(ana.getId(), pichanga.getId(), List.of(png(), "hola".getBytes())))
                .isInstanceOf(BusinessException.class).hasMessageStartingWith("Foto 2:");
        verify(fotos, never()).save(any());
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Borra quien la subió o quien organiza; el resto no")
    void quienBorra() {
        UUID deBeto = UUID.randomUUID();
        FotoPlanResumen foto = resumen(deBeto, pichanga.getId(), grupo.getId(), beto.getId());
        when(fotos.findResumenById(deBeto)).thenReturn(Optional.of(foto));

        assertThatThrownBy(() -> servicio.borrar(ana.getId(), deBeto)).isInstanceOf(ForbiddenException.class);

        miembro(beto, MiembroGrupo.Rol.MIEMBRO);
        servicio.borrar(beto.getId(), deBeto);
        miembro(ana, MiembroGrupo.Rol.ORGANIZADOR);
        servicio.borrar(ana.getId(), deBeto);

        verify(fotos, times(2)).deleteById(deBeto);
    }

    @Test
    @DisplayName("Quien no es del grupo no puede ver ni la miniatura: para esa persona la foto no existe")
    void soloElGrupo() {
        UUID id = UUID.randomUUID();
        FotoPlanResumen foto = resumen(id, pichanga.getId(), grupo.getId(), ana.getId());
        when(fotos.findResumenById(id)).thenReturn(Optional.of(foto));
        when(fotos.miniatura(id)).thenReturn(Optional.of(new byte[]{1}));

        assertThat(servicio.imagen(ana.getId(), id, true)).containsExactly(1);
        assertThatThrownBy(() -> servicio.imagen(beto.getId(), id, true)).isInstanceOf(NotFoundException.class);
        verify(fotos, times(1)).miniatura(id);
    }

    @Test
    @DisplayName("El álbum del grupo lista los planes vividos, del más reciente al más antiguo, con su portada")
    void albumDelGrupo() {
        Plan antiguo = plan("Estudio", LocalDate.of(2026, 10, 10), Plan.Estado.CONFIRMADO);
        Plan futuro = plan("Cena", LocalDate.of(2026, 10, 30), Plan.Estado.CONFIRMADO);
        when(planes.findConfirmadosConFecha(anyCollection())).thenReturn(List.of(antiguo, pichanga, futuro));
        UUID portada = UUID.randomUUID();
        FotoPlanResumen f1 = resumen(portada, pichanga.getId(), grupo.getId(), ana.getId());
        FotoPlanResumen f2 = resumen(UUID.randomUUID(), pichanga.getId(), grupo.getId(), beto.getId());
        when(fotos.findByGrupoIdOrderBySubidaEnAsc(grupo.getId())).thenReturn(List.of(f1, f2));

        AlbumResponses.AlbumGrupo album = servicio.album(ana.getId(), grupo.getId());

        assertThat(album.usadas()).isEqualTo(2);
        assertThat(album.maximo()).isEqualTo(AlbumService.MAX_POR_GRUPO);
        assertThat(album.planes()).extracting(AlbumResponses.PlanConFotos::titulo).containsExactly("Pichanga", "Estudio");
        assertThat(album.planes().get(0).fotos()).isEqualTo(2);
        assertThat(album.planes().get(0).portadaId()).isEqualTo(portada);
        assertThat(album.planes().get(1).portadaId()).isNull();
    }

    @Test
    @DisplayName("En el álbum de un plan, cada quien puede borrar lo suyo y sabe cuántas le quedan")
    void albumDelPlan() {
        FotoPlanResumen mia = resumen(UUID.randomUUID(), pichanga.getId(), grupo.getId(), ana.getId());
        FotoPlanResumen ajena = resumen(UUID.randomUUID(), pichanga.getId(), grupo.getId(), beto.getId());
        when(fotos.findByPlanIdOrderBySubidaEnAsc(pichanga.getId())).thenReturn(List.of(mia, ajena));
        when(fotos.countByGrupoId(grupo.getId())).thenReturn(2L);

        AlbumResponses.AlbumPlan album = servicio.delPlan(ana.getId(), pichanga.getId());

        assertThat(album.puedeSubir()).isTrue();
        assertThat(album.restantesMias()).isEqualTo(9);
        assertThat(album.restantesGrupo()).isEqualTo(58);
        assertThat(album.fotos()).extracting(AlbumResponses.Foto::autorNombre, AlbumResponses.Foto::puedeBorrar)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Ana", true), org.assertj.core.groups.Tuple.tuple("Beto", false));
    }
}
