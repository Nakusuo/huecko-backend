package com.huecko.backend.admin.service;

import com.huecko.backend.admin.dto.UsuarioAdminResponse;
import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.mongo.document.BloqueHorario;
import com.huecko.backend.mongo.repository.AlertaRetrasoRepository;
import com.huecko.backend.mongo.repository.AusenciaRepository;
import com.huecko.backend.mongo.repository.BloqueHorarioRepository;
import com.huecko.backend.mongo.repository.VotacionExpresRepository;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.GrupoRepository;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.postgres.repository.VotoVentanaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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

class AdminUsuariosTest {

    private static Usuario usuario(String email, Usuario.RolSistema rol, Instant alta) {
        return Usuario.builder().id(UUID.randomUUID()).nombre(email).email(email).passwordHash("h")
                .rolSistema(rol).creadoEn(alta).build();
    }

    @Test
    @DisplayName("la lista cuenta grupos, planes y última actividad, con las cuentas nuevas primero")
    void listado() {
        Usuario ana = usuario("ana@h.com", Usuario.RolSistema.USUARIO, Instant.parse("2026-09-01T00:00:00Z"));
        Usuario beto = usuario("beto@h.com", Usuario.RolSistema.USUARIO, Instant.parse("2026-09-10T00:00:00Z"));
        Grupo g = Grupo.builder().id(UUID.randomUUID()).nombre("g").build();
        Instant votoAntiguo = Instant.parse("2026-09-05T00:00:00Z");
        Instant horarioReciente = Instant.parse("2026-09-20T00:00:00Z");

        var datos = new CalculoResumen.Datos(
                List.of(ana, beto), List.of(g),
                List.of(MiembroGrupo.builder().grupo(g).usuario(ana).rol(MiembroGrupo.Rol.ORGANIZADOR).build()),
                List.of(Plan.builder().id(UUID.randomUUID()).grupo(g).creadoPor(ana).creadoEn(votoAntiguo).build()),
                List.of(),
                List.of(BloqueHorario.builder().usuarioId(ana.getId().toString()).actualizadoEn(horarioReciente).build()),
                List.of(), List.of(), List.of(), 7);

        List<UsuarioAdminResponse> filas = ListadoUsuarios.listar(datos);

        assertThat(filas).extracting(UsuarioAdminResponse::email).containsExactly("beto@h.com", "ana@h.com");
        UsuarioAdminResponse filaAna = filas.get(1);
        assertThat(filaAna.grupos()).isEqualTo(1);
        assertThat(filaAna.planesPropuestos()).isEqualTo(1);
        assertThat(filaAna.ultimaActividad()).isEqualTo(horarioReciente);
        assertThat(filas.get(0).ultimaActividad()).isNull();
    }

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);

    private AdminService servicio() {
        when(usuarios.findAll()).thenReturn(List.of());
        return new AdminService(usuarios, mock(GrupoRepository.class), mock(MiembroGrupoRepository.class),
                mock(PlanRepository.class), mock(VotoVentanaRepository.class), mock(BloqueHorarioRepository.class),
                mock(AlertaRetrasoRepository.class), mock(AusenciaRepository.class),
                mock(VotacionExpresRepository.class));
    }

    @Test
    @DisplayName("suspender marca la cuenta y la guarda")
    void suspende() {
        Usuario ana = usuario("ana@h.com", Usuario.RolSistema.USUARIO, Instant.now());
        AdminService servicio = servicio();
        when(usuarios.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(usuarios.findAll()).thenReturn(List.of(ana));

        UsuarioAdminResponse fila = servicio.cambiarSuspension(ana.getId(), true);

        assertThat(ana.isSuspendido()).isTrue();
        assertThat(fila.suspendido()).isTrue();
        verify(usuarios).save(ana);
    }

    @Test
    @DisplayName("a un administrador no se le puede suspender")
    void adminIntocable() {
        Usuario admin = usuario("admin@h.com", Usuario.RolSistema.ADMIN, Instant.now());
        AdminService servicio = servicio();
        when(usuarios.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> servicio.cambiarSuspension(admin.getId(), true))
                .isInstanceOf(BusinessException.class);
        verify(usuarios, never()).save(any());
    }
}
