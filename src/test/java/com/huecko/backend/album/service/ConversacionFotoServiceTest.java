package com.huecko.backend.album.service;

import com.huecko.backend.album.dto.AlbumResponses;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.ForbiddenException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.postgres.entity.ComentarioFoto;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.ReaccionFoto;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.ComentarioFotoRepository;
import com.huecko.backend.postgres.repository.FotoPlanRepository;
import com.huecko.backend.postgres.repository.FotoPlanRepository.FotoPlanResumen;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.ReaccionFotoRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.tiemporeal.ConversacionFoto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
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

/** Reacciones y comentarios: solo el grupo, sin repetir, y cada quien borra lo suyo. */
class ConversacionFotoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-24T20:00:00Z");

    private final FotoPlanRepository fotos = mock(FotoPlanRepository.class);
    private final ReaccionFotoRepository reacciones = mock(ReaccionFotoRepository.class);
    private final ComentarioFotoRepository comentarios = mock(ComentarioFotoRepository.class);
    private final MiembroGrupoRepository miembros = mock(MiembroGrupoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PlanRepository planes = mock(PlanRepository.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final ConversacionFotoService servicio = new ConversacionFotoService(fotos, reacciones, comentarios,
            miembros, usuarios, planes, eventos, Clock.fixed(AHORA, ZoneOffset.UTC));

    private final Grupo grupo = Grupo.builder().id(UUID.randomUUID()).nombre("Proyecto").build();
    private final Usuario ana = Usuario.builder().id(UUID.randomUUID()).nombre("Ana").email("a@h.com").passwordHash("h").build();
    private final Usuario beto = Usuario.builder().id(UUID.randomUUID()).nombre("Beto").email("b@h.com").passwordHash("h").build();
    private final UUID fotoId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();

    private void miembro(Usuario u, MiembroGrupo.Rol rol) {
        when(miembros.existsByGrupo_IdAndUsuario_Id(grupo.getId(), u.getId())).thenReturn(true);
        when(miembros.findByGrupo_IdAndUsuario_Id(grupo.getId(), u.getId()))
                .thenReturn(Optional.of(MiembroGrupo.builder().grupo(grupo).usuario(u).rol(rol).build()));
    }

    @BeforeEach
    void datos() {
        FotoPlanResumen foto = mock(FotoPlanResumen.class);
        when(foto.getId()).thenReturn(fotoId);
        when(foto.getPlanId()).thenReturn(planId);
        when(foto.getGrupoId()).thenReturn(grupo.getId());
        when(foto.getAutorId()).thenReturn(beto.getId()); // la subió Beto
        when(fotos.findResumenById(fotoId)).thenReturn(Optional.of(foto));
        miembro(ana, MiembroGrupo.Rol.MIEMBRO);
        when(usuarios.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(usuarios.findAllById(any())).thenReturn(List.of(ana, beto));
        when(reacciones.deLasFotos(anyCollection())).thenReturn(List.of());
        when(comentarios.contarPorFoto(anyCollection())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Reaccionar guarda la reacción y refresca al grupo; repetirla no hace nada")
    void reaccionar() {
        servicio.reaccionar(ana.getId(), fotoId, ReaccionFoto.Reaccion.CORAZON, true);

        verify(reacciones).save(any(ReaccionFoto.class));
        ArgumentCaptor<ConversacionFoto> evento = ArgumentCaptor.forClass(ConversacionFoto.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().comentario()).isFalse();

        ReaccionFoto.Clave clave = new ReaccionFoto.Clave(fotoId, ana.getId(), ReaccionFoto.Reaccion.CORAZON);
        when(reacciones.existsById(clave)).thenReturn(true);
        servicio.reaccionar(ana.getId(), fotoId, ReaccionFoto.Reaccion.CORAZON, true);
        verify(reacciones).save(any(ReaccionFoto.class));

        servicio.reaccionar(ana.getId(), fotoId, ReaccionFoto.Reaccion.CORAZON, false);
        verify(reacciones).deleteById(clave);
    }

    @Test
    @DisplayName("Fuera del grupo la foto no existe: ni reacciones ni comentarios")
    void soloElGrupo() {
        Usuario intrusa = Usuario.builder().id(UUID.randomUUID()).nombre("X").email("x@h.com").passwordHash("h").build();

        assertThatThrownBy(() -> servicio.reaccionar(intrusa.getId(), fotoId, ReaccionFoto.Reaccion.RISA, true))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> servicio.comentarios(intrusa.getId(), fotoId)).isInstanceOf(NotFoundException.class);
        verify(reacciones, never()).save(any());
    }

    @Test
    @DisplayName("Un comentario se guarda limpio y avisa a quien subió la foto")
    void comentar() {
        servicio.comentar(ana.getId(), fotoId, "  ¡Qué   golazo!  ");

        ArgumentCaptor<ComentarioFoto> guardado = ArgumentCaptor.forClass(ComentarioFoto.class);
        verify(comentarios).save(guardado.capture());
        assertThat(guardado.getValue().getTexto()).isEqualTo("¡Qué golazo!");
        ArgumentCaptor<ConversacionFoto> evento = ArgumentCaptor.forClass(ConversacionFoto.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().comentario()).isTrue();
        assertThat(evento.getValue().autorFotoId()).isEqualTo(beto.getId());
        assertThat(evento.getValue().nombre()).isEqualTo("Ana");

        assertThatThrownBy(() -> servicio.comentar(ana.getId(), fotoId, "   ")).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Un comentario lo borra quien lo escribió o quien organiza; el resto no")
    void borrarComentario() {
        UUID id = UUID.randomUUID();
        when(comentarios.findById(id)).thenReturn(Optional.of(
                ComentarioFoto.builder().id(id).fotoId(fotoId).autorId(beto.getId()).texto("x").creadoEn(AHORA).build()));

        assertThatThrownBy(() -> servicio.borrarComentario(ana.getId(), id)).isInstanceOf(ForbiddenException.class);

        miembro(beto, MiembroGrupo.Rol.MIEMBRO);
        servicio.borrarComentario(beto.getId(), id);
        miembro(ana, MiembroGrupo.Rol.ORGANIZADOR);
        servicio.borrarComentario(ana.getId(), id);
        verify(comentarios, org.mockito.Mockito.times(2)).deleteById(id);
    }

    @Test
    @DisplayName("El resumen cuenta cada reacción en el orden en que se ofrecen, marca las tuyas y suma comentarios")
    void resumen() {
        when(reacciones.deLasFotos(anyCollection())).thenReturn(List.of(
                new ReaccionFoto(new ReaccionFoto.Clave(fotoId, beto.getId(), ReaccionFoto.Reaccion.FUEGO), AHORA),
                new ReaccionFoto(new ReaccionFoto.Clave(fotoId, ana.getId(), ReaccionFoto.Reaccion.CORAZON), AHORA),
                new ReaccionFoto(new ReaccionFoto.Clave(fotoId, beto.getId(), ReaccionFoto.Reaccion.CORAZON), AHORA)));
        when(comentarios.contarPorFoto(anyCollection())).thenReturn(List.<Object[]>of(new Object[]{fotoId, 3L}));

        AlbumResponses.Interacciones i = servicio.resumen(List.of(fotoId), ana.getId()).get(fotoId);

        assertThat(i.reacciones()).containsExactly(
                org.assertj.core.api.Assertions.entry("CORAZON", 2), org.assertj.core.api.Assertions.entry("FUEGO", 1));
        assertThat(i.misReacciones()).containsExactly("CORAZON");
        assertThat(i.comentarios()).isEqualTo(3);
    }
}
