package com.huecko.backend.admin.service;

import com.huecko.backend.admin.dto.GrupoAdminResponse;
import com.huecko.backend.mongo.document.Ausencia;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ListadoGruposTest {

    private static Usuario usuario(String nombre) {
        return Usuario.builder().id(UUID.randomUUID()).nombre(nombre).email(nombre + "@h.com").passwordHash("h").build();
    }

    @Test
    @DisplayName("cuenta integrantes, organizadores, planes por estado e imprevistos; los activos primero")
    void listado() {
        Usuario ana = usuario("Ana");
        Usuario beto = usuario("Beto");
        Grupo activo = Grupo.builder().id(UUID.randomUUID()).nombre("Activo").umbralDisponibilidad(80).build();
        Grupo quieto = Grupo.builder().id(UUID.randomUUID()).nombre("Quieto").build();
        Instant plan = Instant.parse("2026-09-10T00:00:00Z");
        Instant ausencia = Instant.parse("2026-09-20T00:00:00Z");

        var datos = new CalculoResumen.Datos(
                List.of(ana, beto), List.of(quieto, activo),
                List.of(MiembroGrupo.builder().grupo(activo).usuario(ana).rol(MiembroGrupo.Rol.ORGANIZADOR).build(),
                        MiembroGrupo.builder().grupo(activo).usuario(beto).rol(MiembroGrupo.Rol.MIEMBRO).build(),
                        MiembroGrupo.builder().grupo(quieto).usuario(beto).rol(MiembroGrupo.Rol.ORGANIZADOR).build()),
                List.of(Plan.builder().id(UUID.randomUUID()).grupo(activo).creadoPor(ana)
                        .estado(Plan.Estado.CONFIRMADO).creadoEn(plan).build()),
                List.of(), List.of(), List.of(),
                List.of(Ausencia.builder().grupoId(activo.getId().toString()).reportadoEn(ausencia).build()),
                List.of(), 7);

        List<GrupoAdminResponse> filas = ListadoGrupos.listar(datos);

        assertThat(filas).extracting(GrupoAdminResponse::nombre).containsExactly("Activo", "Quieto");
        GrupoAdminResponse g = filas.get(0);
        assertThat(g.miembros()).isEqualTo(2);
        assertThat(g.organizadores()).containsExactly("Ana");
        assertThat(g.planes()).isEqualTo(1);
        assertThat(g.planesPorEstado().get(Plan.Estado.CONFIRMADO)).isEqualTo(1);
        assertThat(g.planesPorEstado().get(Plan.Estado.CANCELADO)).isZero();
        assertThat(g.imprevistos()).isEqualTo(1);
        assertThat(g.ultimaActividad()).isEqualTo(ausencia);
        assertThat(filas.get(1).ultimaActividad()).isNull();
    }
}
