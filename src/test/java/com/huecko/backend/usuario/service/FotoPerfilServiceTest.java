package com.huecko.backend.usuario.service;

import com.huecko.backend.common.Imagenes;
import com.huecko.backend.common.exception.DemasiadosIntentosException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.postgres.entity.FotoPerfil;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.FotoPerfilRepository;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
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

/** La foto de perfil solo la ven tus grupos, y no se puede cambiar sin parar. */
class FotoPerfilServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-23T15:00:00Z");

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final FotoPerfilRepository fotos = mock(FotoPerfilRepository.class);
    private final MiembroGrupoRepository miembros = mock(MiembroGrupoRepository.class);
    private final FotoPerfilService servicio =
            new FotoPerfilService(usuarios, fotos, miembros, Clock.fixed(AHORA, ZoneOffset.UTC));

    private Usuario ana;
    private final UUID beto = UUID.randomUUID();

    @BeforeEach
    void cuenta() {
        ana = Usuario.builder().id(UUID.randomUUID()).nombre("Ana").email("ana@h.com").passwordHash("h").build();
        when(usuarios.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(usuarios.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB), "png", salida);
        return salida.toByteArray();
    }

    @Test
    @DisplayName("Guardar rehace la foto y marca cuándo cambió, para que los grupos la vuelvan a pedir")
    void guardar() throws IOException {
        ArgumentCaptor<FotoPerfil> guardada = ArgumentCaptor.forClass(FotoPerfil.class);

        Usuario resultado = servicio.guardar(ana.getId(), png());

        verify(fotos).save(guardada.capture());
        assertThat(guardada.getValue().getUsuarioId()).isEqualTo(ana.getId());
        assertThat(Imagenes.esJpeg(guardada.getValue().getContenido())).isTrue();
        assertThat(resultado.getFotoActualizadaEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("Después de " + FotoPerfilService.MAX_CAMBIOS + " cambios en una hora, hay que esperar")
    void limiteDeCambios() throws IOException {
        byte[] foto = png();
        for (int i = 0; i < FotoPerfilService.MAX_CAMBIOS; i++) {
            servicio.guardar(ana.getId(), foto);
        }

        assertThatThrownBy(() -> servicio.guardar(ana.getId(), foto)).isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    @DisplayName("Quitarla borra los bytes y deja la cuenta sin foto")
    void quitar() {
        ana.setFotoActualizadaEn(AHORA);

        servicio.quitar(ana.getId());

        verify(fotos).deleteById(ana.getId());
        assertThat(ana.getFotoActualizadaEn()).isNull();
    }

    @Test
    @DisplayName("Solo la ve quien comparte un grupo; para el resto, la foto no existe")
    void soloTusGrupos() {
        FotoPerfil foto = new FotoPerfil(ana.getId(), new byte[]{1}, AHORA);
        when(fotos.findById(ana.getId())).thenReturn(Optional.of(foto));

        assertThat(servicio.ver(ana.getId(), ana.getId())).isSameAs(foto);

        when(miembros.gruposEnComun(beto, ana.getId())).thenReturn(List.of());
        assertThatThrownBy(() -> servicio.ver(beto, ana.getId())).isInstanceOf(NotFoundException.class);

        when(miembros.gruposEnComun(beto, ana.getId())).thenReturn(List.of(Grupo.builder().nombre("Proyecto").build()));
        assertThat(servicio.ver(beto, ana.getId())).isSameAs(foto);
    }

    @Test
    @DisplayName("Sin grupo en común ni siquiera se consulta si la foto existe")
    void noRevelaSiExiste() {
        when(miembros.gruposEnComun(beto, ana.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.ver(beto, ana.getId())).isInstanceOf(NotFoundException.class);
        verify(fotos, never()).findById(any());
    }
}
