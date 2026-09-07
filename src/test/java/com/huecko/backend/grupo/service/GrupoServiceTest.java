package com.huecko.backend.grupo.service;

import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.ForbiddenException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.grupo.dto.GrupoRequests;
import com.huecko.backend.grupo.dto.GrupoResponse;
import com.huecko.backend.mongo.document.BloqueHorario;
import com.huecko.backend.mongo.repository.BloqueHorarioRepository;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.GrupoRepository;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de pertenencia y de permisos del Módulo 2. Con el repositorio simulado:
 * lo que se comprueba aquí es quién puede hacer qué, no que la consulta SQL
 * devuelva filas.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GrupoServiceTest {

    private static final UUID GRUPO = UUID.randomUUID();

    @Mock private GrupoRepository grupoRepository;
    @Mock private MiembroGrupoRepository miembroGrupoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private BloqueHorarioRepository bloqueHorarioRepository;

    private final CalculadoraDisponibilidad calculadora = new CalculadoraDisponibilidad();

    private GrupoService servicio() {
        return new GrupoService(grupoRepository, miembroGrupoRepository, usuarioRepository,
                bloqueHorarioRepository, calculadora);
    }

    /* ------------------------------------------------------------------ *
     * Crear
     * ------------------------------------------------------------------ */

    @Test
    @DisplayName("Quien crea el grupo queda de organizador y como imprescindible")
    void elCreadorEsOrganizador() {
        Usuario ana = usuario("Ana");
        when(usuarioRepository.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(grupoRepository.existsByCodigoInvitacionIgnoreCase(anyString())).thenReturn(false);
        when(grupoRepository.save(any())).thenAnswer(i -> {
            Grupo g = i.getArgument(0);
            g.setId(GRUPO);
            return g;
        });
        when(miembroGrupoRepository.findByGrupoIdConUsuario(GRUPO)).thenReturn(List.of());

        servicio().crear(ana.getId(), new GrupoRequests.Crear("Amigos", "  con espacios  ", null));

        ArgumentCaptor<MiembroGrupo> membresia = ArgumentCaptor.forClass(MiembroGrupo.class);
        verify(miembroGrupoRepository).save(membresia.capture());
        assertThat(membresia.getValue().getRol()).isEqualTo(MiembroGrupo.Rol.ORGANIZADOR);
        assertThat(membresia.getValue().isEsImprescindible()).isTrue();

        ArgumentCaptor<Grupo> guardado = ArgumentCaptor.forClass(Grupo.class);
        verify(grupoRepository).save(guardado.capture());
        assertThat(guardado.getValue().getDescripcion()).isEqualTo("con espacios");
        // Sin umbral explícito manda la unanimidad (HU-05).
        assertThat(guardado.getValue().getUmbralDisponibilidad()).isEqualTo(100);
        assertThat(guardado.getValue().getCodigoInvitacion()).hasSize(8);
    }

    @Test
    @DisplayName("El código de invitación evita los caracteres que se confunden al dictarlo")
    void elCodigoNoUsaCaracteresAmbiguos() {
        Usuario ana = usuario("Ana");
        when(usuarioRepository.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(grupoRepository.existsByCodigoInvitacionIgnoreCase(anyString())).thenReturn(false);
        when(grupoRepository.save(any())).thenAnswer(i -> {
            Grupo g = i.getArgument(0);
            g.setId(GRUPO);
            return g;
        });
        when(miembroGrupoRepository.findByGrupoIdConUsuario(GRUPO)).thenReturn(List.of());

        for (int i = 0; i < 50; i++) {
            servicio().crear(ana.getId(), new GrupoRequests.Crear("Amigos", null, 80));
        }

        ArgumentCaptor<Grupo> guardado = ArgumentCaptor.forClass(Grupo.class);
        verify(grupoRepository, org.mockito.Mockito.atLeast(50)).save(guardado.capture());
        assertThat(guardado.getAllValues())
                .allSatisfy(g -> assertThat(g.getCodigoInvitacion()).doesNotContainAnyWhitespaces()
                        .matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}"));
    }

    @Test
    @DisplayName("Si el código sorteado ya existe se vuelve a intentar en vez de reventar")
    void reintentaAnteUnaColisionDeCodigo() {
        Usuario ana = usuario("Ana");
        when(usuarioRepository.findById(ana.getId())).thenReturn(Optional.of(ana));
        // El primero choca, el segundo está libre.
        when(grupoRepository.existsByCodigoInvitacionIgnoreCase(anyString()))
                .thenReturn(true)
                .thenReturn(false);
        when(grupoRepository.save(any())).thenAnswer(i -> {
            Grupo g = i.getArgument(0);
            g.setId(GRUPO);
            return g;
        });
        when(miembroGrupoRepository.findByGrupoIdConUsuario(GRUPO)).thenReturn(List.of());

        GrupoResponse r = servicio().crear(ana.getId(), new GrupoRequests.Crear("Amigos", null, null));

        assertThat(r.codigoInvitacion()).hasSize(8);
        verify(grupoRepository, org.mockito.Mockito.times(2)).existsByCodigoInvitacionIgnoreCase(anyString());
    }

    /* ------------------------------------------------------------------ *
     * Pertenencia
     * ------------------------------------------------------------------ */

    @Test
    @DisplayName("Un grupo ajeno se responde como inexistente, para no confirmar que ese id existe")
    void elGrupoAjenoNoSeDistingueDeUnoInexistente() {
        UUID intruso = UUID.randomUUID();
        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, intruso)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio().detalle(intruso, GRUPO))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Un miembro raso no puede cambiar el umbral del grupo")
    void soloElOrganizadorCambiaElUmbral() {
        MiembroGrupo raso = membresia(usuario("Bruno"), MiembroGrupo.Rol.MIEMBRO);
        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, raso.getUsuario().getId()))
                .thenReturn(Optional.of(raso));

        assertThatThrownBy(() -> servicio().actualizar(raso.getUsuario().getId(), GRUPO,
                new GrupoRequests.Actualizar(null, null, 70)))
                .isInstanceOf(ForbiddenException.class);

        verify(grupoRepository, never()).save(any());
    }

    @Test
    @DisplayName("RF-06: el organizador sí puede bajar el umbral")
    void elOrganizadorCambiaElUmbral() {
        MiembroGrupo jefa = membresia(usuario("Ana"), MiembroGrupo.Rol.ORGANIZADOR);
        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, jefa.getUsuario().getId()))
                .thenReturn(Optional.of(jefa));
        when(grupoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(miembroGrupoRepository.findByGrupoIdConUsuario(GRUPO)).thenReturn(List.of(jefa));

        GrupoResponse r = servicio().actualizar(jefa.getUsuario().getId(), GRUPO,
                new GrupoRequests.Actualizar(null, null, 70));

        assertThat(r.umbralDisponibilidad()).isEqualTo(70);
    }

    /* ------------------------------------------------------------------ *
     * Unirse
     * ------------------------------------------------------------------ */

    @Test
    @DisplayName("Un código que no existe da 404")
    void codigoInexistenteDa404() {
        when(grupoRepository.findByCodigoInvitacionIgnoreCase("NOEXISTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio().unirse(UUID.randomUUID(), new GrupoRequests.Unirse("NOEXISTE")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Unirse dos veces no duplica la membresía ni da error: el resultado pedido ya se cumple")
    void unirseDosVecesEsIdempotente() {
        Usuario bruno = usuario("Bruno");
        Grupo grupo = grupo();
        when(grupoRepository.findByCodigoInvitacionIgnoreCase("HUECKO26")).thenReturn(Optional.of(grupo));
        when(miembroGrupoRepository.existsByGrupo_IdAndUsuario_Id(GRUPO, bruno.getId())).thenReturn(true);
        when(miembroGrupoRepository.findByGrupoIdConUsuario(GRUPO)).thenReturn(List.of());

        servicio().unirse(bruno.getId(), new GrupoRequests.Unirse("HUECKO26"));

        verify(miembroGrupoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Quien se une entra de miembro raso, no de organizador")
    void quienSeUneEsMiembroRaso() {
        Usuario bruno = usuario("Bruno");
        Grupo grupo = grupo();
        when(grupoRepository.findByCodigoInvitacionIgnoreCase("HUECKO26")).thenReturn(Optional.of(grupo));
        when(miembroGrupoRepository.existsByGrupo_IdAndUsuario_Id(GRUPO, bruno.getId())).thenReturn(false);
        when(usuarioRepository.findById(bruno.getId())).thenReturn(Optional.of(bruno));
        when(miembroGrupoRepository.findByGrupoIdConUsuario(GRUPO)).thenReturn(List.of());

        servicio().unirse(bruno.getId(), new GrupoRequests.Unirse("  HUECKO26  "));

        ArgumentCaptor<MiembroGrupo> nuevo = ArgumentCaptor.forClass(MiembroGrupo.class);
        verify(miembroGrupoRepository).save(nuevo.capture());
        assertThat(nuevo.getValue().getRol()).isEqualTo(MiembroGrupo.Rol.MIEMBRO);
        assertThat(nuevo.getValue().isEsImprescindible()).isFalse();
    }

    /* ------------------------------------------------------------------ *
     * Salir y expulsar
     * ------------------------------------------------------------------ */

    @Test
    @DisplayName("Un miembro raso no puede sacar a otro del grupo")
    void elMiembroRasoNoExpulsa() {
        MiembroGrupo raso = membresia(usuario("Bruno"), MiembroGrupo.Rol.MIEMBRO);
        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, raso.getUsuario().getId()))
                .thenReturn(Optional.of(raso));

        assertThatThrownBy(() -> servicio().salir(raso.getUsuario().getId(), GRUPO, UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);

        verify(miembroGrupoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("El único organizador no puede irse dejando gente dentro")
    void elUnicoOrganizadorNoAbandonaElGrupo() {
        MiembroGrupo jefa = membresia(usuario("Ana"), MiembroGrupo.Rol.ORGANIZADOR);
        MiembroGrupo raso = membresia(usuario("Bruno"), MiembroGrupo.Rol.MIEMBRO);
        UUID anaId = jefa.getUsuario().getId();

        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, anaId)).thenReturn(Optional.of(jefa));
        when(miembroGrupoRepository.findByGrupoIdConUsuario(GRUPO)).thenReturn(List.of(jefa, raso));
        when(miembroGrupoRepository.countByGrupo_Id(GRUPO)).thenReturn(2L);

        assertThatThrownBy(() -> servicio().salir(anaId, GRUPO, anaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("único organizador");

        verify(miembroGrupoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Cuando se va la última persona el grupo se borra en vez de quedar huérfano")
    void elGrupoVacioSeBorra() {
        MiembroGrupo jefa = membresia(usuario("Ana"), MiembroGrupo.Rol.ORGANIZADOR);
        UUID anaId = jefa.getUsuario().getId();

        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, anaId)).thenReturn(Optional.of(jefa));
        when(miembroGrupoRepository.findByGrupoIdConUsuario(GRUPO)).thenReturn(List.of(jefa));
        when(miembroGrupoRepository.countByGrupo_Id(GRUPO)).thenReturn(1L, 0L);

        servicio().salir(anaId, GRUPO, anaId);

        verify(miembroGrupoRepository).delete(jefa);
        verify(grupoRepository).delete(jefa.getGrupo());
    }

    @Test
    @DisplayName("Degradar al único organizador se rechaza: el grupo quedaría sin quien lo administre")
    void noSePuedeDegradarAlUnicoOrganizador() {
        MiembroGrupo jefa = membresia(usuario("Ana"), MiembroGrupo.Rol.ORGANIZADOR);
        UUID anaId = jefa.getUsuario().getId();

        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, anaId)).thenReturn(Optional.of(jefa));
        when(miembroGrupoRepository.findByGrupoIdConUsuario(GRUPO)).thenReturn(List.of(jefa));

        assertThatThrownBy(() -> servicio().actualizarMiembro(anaId, GRUPO, anaId,
                new GrupoRequests.ActualizarMiembro(MiembroGrupo.Rol.MIEMBRO, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("HU-14: el organizador marca a alguien como imprescindible")
    void elOrganizadorMarcaImprescindible() {
        MiembroGrupo jefa = membresia(usuario("Ana"), MiembroGrupo.Rol.ORGANIZADOR);
        MiembroGrupo raso = membresia(usuario("Bruno"), MiembroGrupo.Rol.MIEMBRO);
        UUID anaId = jefa.getUsuario().getId();
        UUID brunoId = raso.getUsuario().getId();

        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, anaId)).thenReturn(Optional.of(jefa));
        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, brunoId)).thenReturn(Optional.of(raso));
        when(miembroGrupoRepository.findByGrupoIdConUsuario(GRUPO)).thenReturn(List.of(jefa, raso));

        servicio().actualizarMiembro(anaId, GRUPO, brunoId,
                new GrupoRequests.ActualizarMiembro(null, true));

        assertThat(raso.isEsImprescindible()).isTrue();
        assertThat(raso.getRol()).isEqualTo(MiembroGrupo.Rol.MIEMBRO);
    }

    /* ------------------------------------------------------------------ *
     * Disponibilidad
     * ------------------------------------------------------------------ */

    @Test
    @DisplayName("RNF-06: el cruce solo mira bloques CONFIRMADOS, nunca borradores de OCR")
    void elCruceIgnoraLosBorradores() {
        MiembroGrupo jefa = membresia(usuario("Ana"), MiembroGrupo.Rol.ORGANIZADOR);
        UUID anaId = jefa.getUsuario().getId();

        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, anaId)).thenReturn(Optional.of(jefa));
        when(miembroGrupoRepository.findUsuarioIdsByGrupoId(GRUPO)).thenReturn(List.of(anaId));
        when(bloqueHorarioRepository.findByUsuarioIdInAndEstado(any(), any())).thenReturn(List.of());

        servicio().disponibilidad(anaId, GRUPO, null, null);

        verify(bloqueHorarioRepository).findByUsuarioIdInAndEstado(
                List.of(anaId.toString()), BloqueHorario.Estado.CONFIRMADO);
    }

    @Test
    @DisplayName("Sin umbral en la petición se usa el que tiene guardado el grupo")
    void sinUmbralSeUsaElDelGrupo() {
        MiembroGrupo jefa = membresia(usuario("Ana"), MiembroGrupo.Rol.ORGANIZADOR);
        jefa.getGrupo().setUmbralDisponibilidad(70);
        UUID anaId = jefa.getUsuario().getId();

        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, anaId)).thenReturn(Optional.of(jefa));
        when(miembroGrupoRepository.findUsuarioIdsByGrupoId(GRUPO)).thenReturn(List.of(anaId));
        when(bloqueHorarioRepository.findByUsuarioIdInAndEstado(any(), any())).thenReturn(List.of());

        assertThat(servicio().disponibilidad(anaId, GRUPO, null, null).umbral()).isEqualTo(70);
        // Y el de la petición manda sobre el guardado, sin cambiarlo.
        assertThat(servicio().disponibilidad(anaId, GRUPO, 90, null).umbral()).isEqualTo(90);
        assertThat(jefa.getGrupo().getUmbralDisponibilidad()).isEqualTo(70);
    }

    @Test
    @DisplayName("Quien no pertenece al grupo no puede mirar su heatmap")
    void elHeatmapNoEsPublico() {
        UUID intruso = UUID.randomUUID();
        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, intruso)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio().disponibilidad(intruso, GRUPO, null, null))
                .isInstanceOf(NotFoundException.class);

        verify(bloqueHorarioRepository, never()).findByUsuarioIdInAndEstado(any(), any());
    }

    /* ------------------------------------------------------------------ */

    private Usuario usuario(String nombre) {
        return Usuario.builder()
                .id(UUID.randomUUID())
                .nombre(nombre)
                .email(nombre.toLowerCase() + "@huecko.com")
                .passwordHash("x")
                .build();
    }

    private Grupo grupo() {
        return Grupo.builder()
                .id(GRUPO)
                .nombre("Proyecto Integrador")
                .codigoInvitacion("HUECKO26")
                .umbralDisponibilidad(80)
                .creadoPor(usuario("Ana"))
                .build();
    }

    private MiembroGrupo membresia(Usuario usuario, MiembroGrupo.Rol rol) {
        return MiembroGrupo.builder()
                .grupo(grupo())
                .usuario(usuario)
                .rol(rol)
                .esImprescindible(false)
                .build();
    }
}
