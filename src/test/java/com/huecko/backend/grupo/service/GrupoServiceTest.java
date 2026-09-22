package com.huecko.backend.grupo.service;

import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.ForbiddenException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.grupo.dto.GrupoRequests;
import com.huecko.backend.grupo.dto.GrupoResponse;
import com.huecko.backend.mongo.document.BloqueHorario;
import com.huecko.backend.mongo.repository.BloqueHorarioRepository;
import com.huecko.backend.mongo.repository.VotacionExpresOperaciones;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.entity.VotoVentana;
import com.huecko.backend.postgres.repository.GrupoRepository;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.postgres.repository.VotoVentanaRepository;
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
    @Mock private PlanRepository planRepository;
    @Mock private VotoVentanaRepository votoVentanaRepository;
    @Mock private VotacionExpresOperaciones votacionExpresOperaciones;

    private final CalculadoraDisponibilidad calculadora = new CalculadoraDisponibilidad();

    private GrupoService servicio() {
        return new GrupoService(grupoRepository, miembroGrupoRepository, usuarioRepository,
                bloqueHorarioRepository, calculadora, planRepository, votoVentanaRepository,
                votacionExpresOperaciones);
    }

    /* ------------------------------------------------------------------ *
     * Crear
     * ------------------------------------------------------------------ */

    @Test
    @DisplayName("Quien crea el grupo queda de organizador y como imprescindible")
    void elCreadorEsOrganizador() {
        Usuario ana = usuario("Ana");
        when(usuarioRepository.findById(ana.getId())).thenReturn(Optional.of(ana));
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
     * Agregar integrantes
     *
     * Sustituye a las pruebas de "unirse por código". Lo que cambia no es solo
     * el dato de entrada: antes cualquiera con la cadena entraba solo, ahora la
     * entrada la decide el organizador, y eso hay que vigilarlo.
     * ------------------------------------------------------------------ */

    @Test
    @DisplayName("El organizador agrega por correo y quien entra queda de miembro raso")
    void elOrganizadorAgregaPorCorreo() {
        MiembroGrupo jefa = membresia(usuario("Ana"), MiembroGrupo.Rol.ORGANIZADOR);
        Usuario bruno = usuario("Bruno");
        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, jefa.getUsuario().getId()))
                .thenReturn(Optional.of(jefa));
        when(usuarioRepository.findByEmailIgnoreCase("bruno@huecko.com")).thenReturn(Optional.of(bruno));
        when(miembroGrupoRepository.existsByGrupo_IdAndUsuario_Id(GRUPO, bruno.getId())).thenReturn(false);
        when(miembroGrupoRepository.findByGrupoIdConUsuario(GRUPO)).thenReturn(List.of(jefa));

        servicio().agregarMiembro(jefa.getUsuario().getId(), GRUPO, "  bruno@huecko.com  ");

        ArgumentCaptor<MiembroGrupo> nuevo = ArgumentCaptor.forClass(MiembroGrupo.class);
        verify(miembroGrupoRepository).save(nuevo.capture());
        assertThat(nuevo.getValue().getUsuario()).isEqualTo(bruno);
        assertThat(nuevo.getValue().getRol()).isEqualTo(MiembroGrupo.Rol.MIEMBRO);
        assertThat(nuevo.getValue().isEsImprescindible()).isFalse();
    }

    @Test
    @DisplayName("Un miembro raso NO puede meter gente en el grupo")
    void elMiembroRasoNoAgrega() {
        MiembroGrupo bruno = membresia(usuario("Bruno"), MiembroGrupo.Rol.MIEMBRO);
        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, bruno.getUsuario().getId()))
                .thenReturn(Optional.of(bruno));

        assertThatThrownBy(() ->
                servicio().agregarMiembro(bruno.getUsuario().getId(), GRUPO, "carla@huecko.com"))
                .isInstanceOf(ForbiddenException.class);

        verify(miembroGrupoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un correo sin cuenta en Huecko da 404 en vez de crear a nadie")
    void elCorreoSinCuentaDa404() {
        MiembroGrupo jefa = membresia(usuario("Ana"), MiembroGrupo.Rol.ORGANIZADOR);
        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, jefa.getUsuario().getId()))
                .thenReturn(Optional.of(jefa));
        when(usuarioRepository.findByEmailIgnoreCase("nadie@huecko.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                servicio().agregarMiembro(jefa.getUsuario().getId(), GRUPO, "nadie@huecko.com"))
                .isInstanceOf(NotFoundException.class);

        verify(miembroGrupoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Agregar dos veces a la misma persona no duplica la membresía")
    void agregarDosVecesEsIdempotente() {
        MiembroGrupo jefa = membresia(usuario("Ana"), MiembroGrupo.Rol.ORGANIZADOR);
        Usuario bruno = usuario("Bruno");
        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, jefa.getUsuario().getId()))
                .thenReturn(Optional.of(jefa));
        when(usuarioRepository.findByEmailIgnoreCase("bruno@huecko.com")).thenReturn(Optional.of(bruno));
        when(miembroGrupoRepository.existsByGrupo_IdAndUsuario_Id(GRUPO, bruno.getId())).thenReturn(true);
        when(miembroGrupoRepository.findByGrupoIdConUsuario(GRUPO)).thenReturn(List.of(jefa));

        servicio().agregarMiembro(jefa.getUsuario().getId(), GRUPO, "bruno@huecko.com");

        verify(miembroGrupoRepository, never()).save(any());
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
    @DisplayName("Si el grupo vacío tiene planes no se borra: la clave foránea lo impediría")
    void elGrupoVacioConPlanesNoSeBorra() {
        MiembroGrupo jefa = membresia(usuario("Ana"), MiembroGrupo.Rol.ORGANIZADOR);
        UUID anaId = jefa.getUsuario().getId();

        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, anaId)).thenReturn(Optional.of(jefa));
        when(miembroGrupoRepository.findByGrupoIdConUsuario(GRUPO)).thenReturn(List.of(jefa));
        when(miembroGrupoRepository.countByGrupo_Id(GRUPO)).thenReturn(1L, 0L);
        when(grupoRepository.tienePlanes(GRUPO)).thenReturn(true);

        servicio().salir(anaId, GRUPO, anaId);

        verify(miembroGrupoRepository).delete(jefa);
        verify(grupoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Quien sale del grupo se lleva sus votos de planes en votación y de votaciones exprés abiertas")
    void alSalirSeBorranSusVotosAbiertos() {
        MiembroGrupo jefa = membresia(usuario("Ana"), MiembroGrupo.Rol.ORGANIZADOR);
        MiembroGrupo raso = membresia(usuario("Bruno"), MiembroGrupo.Rol.MIEMBRO);
        UUID brunoId = raso.getUsuario().getId();
        List<VotoVentana> votosDeBruno = List.of(VotoVentana.builder().usuario(raso.getUsuario()).build());

        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, brunoId)).thenReturn(Optional.of(raso));
        when(miembroGrupoRepository.findByGrupoIdConUsuario(GRUPO)).thenReturn(List.of(jefa, raso));
        when(miembroGrupoRepository.countByGrupo_Id(GRUPO)).thenReturn(2L, 1L);
        when(votoVentanaRepository.findByUsuarioEnGrupoConEstado(brunoId, GRUPO, Plan.Estado.PROPUESTO))
                .thenReturn(votosDeBruno);

        servicio().salir(brunoId, GRUPO, brunoId);

        verify(votoVentanaRepository).deleteAll(votosDeBruno);
        verify(votacionExpresOperaciones).retirarVotosDe(GRUPO.toString(), brunoId.toString());
        // Quedan integrantes: sus planes siguen su curso.
        verify(planRepository, never()).findByGrupo_IdAndEstado(any(), any());
    }

    @Test
    @DisplayName("Sacar a alguien también le quita los votos abiertos")
    void alExpulsarSeBorranSusVotos() {
        MiembroGrupo jefa = membresia(usuario("Ana"), MiembroGrupo.Rol.ORGANIZADOR);
        MiembroGrupo raso = membresia(usuario("Bruno"), MiembroGrupo.Rol.MIEMBRO);
        UUID anaId = jefa.getUsuario().getId();
        UUID brunoId = raso.getUsuario().getId();

        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, anaId)).thenReturn(Optional.of(jefa));
        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, brunoId)).thenReturn(Optional.of(raso));
        when(miembroGrupoRepository.countByGrupo_Id(GRUPO)).thenReturn(1L);

        servicio().salir(anaId, GRUPO, brunoId);

        verify(votoVentanaRepository).findByUsuarioEnGrupoConEstado(brunoId, GRUPO, Plan.Estado.PROPUESTO);
        verify(votacionExpresOperaciones).retirarVotosDe(GRUPO.toString(), brunoId.toString());
    }

    @Test
    @DisplayName("Si el grupo queda vacío con planes, los que siguen en votación se cancelan")
    void elGrupoVacioCancelaSusPlanesEnVotacion() {
        MiembroGrupo jefa = membresia(usuario("Ana"), MiembroGrupo.Rol.ORGANIZADOR);
        UUID anaId = jefa.getUsuario().getId();
        Plan abierto = Plan.builder().id(UUID.randomUUID()).estado(Plan.Estado.PROPUESTO).build();

        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, anaId)).thenReturn(Optional.of(jefa));
        when(miembroGrupoRepository.findByGrupoIdConUsuario(GRUPO)).thenReturn(List.of(jefa));
        when(miembroGrupoRepository.countByGrupo_Id(GRUPO)).thenReturn(1L, 0L);
        when(grupoRepository.tienePlanes(GRUPO)).thenReturn(true);
        when(planRepository.findByGrupo_IdAndEstado(GRUPO, Plan.Estado.PROPUESTO)).thenReturn(List.of(abierto));

        servicio().salir(anaId, GRUPO, anaId);

        assertThat(abierto.getEstado()).isEqualTo(Plan.Estado.CANCELADO);
        assertThat(abierto.getCerradoEn()).isNotNull();
        verify(planRepository).saveAll(List.of(abierto));
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
    @DisplayName("El umbral de la consulta tiene los mismos límites que el del grupo: 50 a 100")
    void elUmbralDeLaConsultaVaDe50A100() {
        MiembroGrupo jefa = membresia(usuario("Ana"), MiembroGrupo.Rol.ORGANIZADOR);
        UUID anaId = jefa.getUsuario().getId();

        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, anaId)).thenReturn(Optional.of(jefa));
        when(miembroGrupoRepository.findUsuarioIdsByGrupoId(GRUPO)).thenReturn(List.of(anaId));
        when(bloqueHorarioRepository.findByUsuarioIdInAndEstado(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> servicio().disponibilidad(anaId, GRUPO, 49, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("El umbral debe estar entre 50 y 100");
        assertThatThrownBy(() -> servicio().disponibilidad(anaId, GRUPO, 101, null))
                .isInstanceOf(BusinessException.class);
        assertThat(servicio().disponibilidad(anaId, GRUPO, 50, null).umbral()).isEqualTo(50);
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
