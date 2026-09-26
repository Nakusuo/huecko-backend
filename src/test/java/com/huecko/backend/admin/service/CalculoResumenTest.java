package com.huecko.backend.admin.service;

import com.huecko.backend.admin.dto.ResumenAdminResponse;
import com.huecko.backend.mongo.document.AlertaRetraso;
import com.huecko.backend.mongo.document.Ausencia;
import com.huecko.backend.mongo.document.BloqueHorario;
import com.huecko.backend.mongo.document.VotacionExpres;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.entity.VotoVentana;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CalculoResumenTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** Jueves 24 de septiembre de 2026, mediodía en Lima. */
    private static final Instant AHORA = Instant.parse("2026-09-24T17:00:00Z");

    private static Instant haceDias(int dias) {
        return AHORA.minus(Duration.ofDays(dias));
    }

    private static Usuario usuario(int diasAlta, Usuario.RolSistema rol) {
        return Usuario.builder().id(UUID.randomUUID()).nombre("x").email(UUID.randomUUID() + "@h.com")
                .passwordHash("h").rolSistema(rol).creadoEn(haceDias(diasAlta)).build();
    }

    private static Grupo grupo() {
        return Grupo.builder().id(UUID.randomUUID()).nombre("g").build();
    }

    private static Plan plan(Grupo grupo, Usuario autor, Plan.Estado estado, int diasCreado, Integer diasCerrado) {
        return Plan.builder().id(UUID.randomUUID()).grupo(grupo).creadoPor(autor).titulo("p").estado(estado)
                .creadoEn(haceDias(diasCreado)).cerradoEn(diasCerrado == null ? null : haceDias(diasCerrado)).build();
    }

    private static CalculoResumen.Datos datos(List<Usuario> usuarios, List<Grupo> grupos, List<MiembroGrupo> miembros,
                                              List<Plan> planes, List<VotoVentana> votos, List<BloqueHorario> bloques,
                                              List<AlertaRetraso> retrasos, List<Ausencia> ausencias,
                                              List<VotacionExpres> votaciones) {
        return new CalculoResumen.Datos(usuarios, grupos, miembros, planes, votos, bloques, retrasos, ausencias,
                votaciones, 7);
    }

    private static ResumenAdminResponse calcular(CalculoResumen.Datos d) {
        return CalculoResumen.calcular(d, AHORA, LIMA);
    }

    @Test
    @DisplayName("sin datos todo queda a cero, con las 8 semanas presentes y sin dividir por cero")
    void vacio() {
        var r = calcular(datos(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of()));

        assertThat(r.usuarios().total()).isZero();
        assertThat(r.grupos().miembrosMedio()).isZero();
        assertThat(r.planes().tasaConcrecion()).isNull();
        assertThat(r.imprevistos().minutosRetrasoMedio()).isNull();
        assertThat(r.planes().porEstado()).containsOnlyKeys(Plan.Estado.values());
        assertThat(r.semanas()).hasSize(CalculoResumen.SEMANAS);
        assertThat(r.semanas().get(CalculoResumen.SEMANAS - 1).inicio()).isEqualTo(LocalDate.of(2026, 9, 21));
    }

    @Test
    @DisplayName("los admins no cuentan como usuarios del producto")
    void adminsAparte() {
        Usuario ana = usuario(3, Usuario.RolSistema.USUARIO);
        Usuario viejo = usuario(40, Usuario.RolSistema.USUARIO);
        Usuario admin = usuario(1, Usuario.RolSistema.ADMIN);

        var r = calcular(datos(List.of(ana, viejo, admin), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of()));

        assertThat(r.usuarios().total()).isEqualTo(2);
        assertThat(r.usuarios().nuevos7d()).isEqualTo(1);
        assertThat(r.usuarios().admins()).isEqualTo(1);
        assertThat(r.semanas().get(CalculoResumen.SEMANAS - 1).altas()).isEqualTo(1);
    }

    @Test
    @DisplayName("activo = hizo algo en 30 días; tener horario confirmado se cuenta aparte")
    void actividad() {
        Usuario proponeHoy = usuario(60, Usuario.RolSistema.USUARIO);
        Usuario soloHorarioViejo = usuario(60, Usuario.RolSistema.USUARIO);
        Usuario avisaRetraso = usuario(60, Usuario.RolSistema.USUARIO);
        Grupo g = grupo();

        BloqueHorario bloqueViejo = BloqueHorario.builder().usuarioId(soloHorarioViejo.getId().toString())
                .estado(BloqueHorario.Estado.CONFIRMADO).actualizadoEn(haceDias(90)).build();
        BloqueHorario borrador = BloqueHorario.builder().usuarioId(proponeHoy.getId().toString())
                .estado(BloqueHorario.Estado.BORRADOR).actualizadoEn(haceDias(90)).build();
        AlertaRetraso retraso = AlertaRetraso.builder().usuarioId(avisaRetraso.getId().toString())
                .grupoId(g.getId().toString()).minutosEstimados(10).actualizadoEn(haceDias(2)).build();

        var r = calcular(datos(List.of(proponeHoy, soloHorarioViejo, avisaRetraso), List.of(g), List.of(),
                List.of(plan(g, proponeHoy, Plan.Estado.PROPUESTO, 1, null)), List.of(),
                List.of(bloqueViejo, borrador), List.of(retraso), List.of(), List.of()));

        assertThat(r.usuarios().activos30d()).isEqualTo(2);
        assertThat(r.usuarios().conHorario()).isEqualTo(1);
        assertThat(r.grupos().activos30d()).isEqualTo(1);
    }

    @Test
    @DisplayName("la concreción solo mira los planes cerrados")
    void concrecion() {
        Usuario u = usuario(10, Usuario.RolSistema.USUARIO);
        Grupo g = grupo();
        Grupo sinPlanes = grupo();

        var r = calcular(datos(List.of(u), List.of(g, sinPlanes), List.of(), List.of(
                        plan(g, u, Plan.Estado.CONFIRMADO, 5, 2),
                        plan(g, u, Plan.Estado.CONFIRMADO, 5, 20),
                        plan(g, u, Plan.Estado.CANCELADO, 5, 3),
                        plan(g, u, Plan.Estado.PROPUESTO, 1, null)),
                List.of(), List.of(), List.of(), List.of(), List.of()));

        assertThat(r.planes().total()).isEqualTo(4);
        assertThat(r.planes().tasaConcrecion()).isEqualTo(0.67);
        assertThat(r.planes().porEstado().get(Plan.Estado.EN_RECOORDINACION)).isZero();
        assertThat(r.grupos().sinPlanes()).isEqualTo(1);
        // Confirmado hace 2 días: cae en la semana actual (desde el lunes 21).
        assertThat(r.semanas().get(CalculoResumen.SEMANAS - 1).planesConfirmados()).isEqualTo(1);
    }

    @Test
    @DisplayName("imprevistos: ausencias críticas, retraso medio y resultados de votaciones cerradas")
    void imprevistos() {
        var ausencias = List.of(
                Ausencia.builder().critica(true).reportadoEn(haceDias(1)).build(),
                Ausencia.builder().critica(false).reportadoEn(haceDias(1)).build());
        var retrasos = List.of(
                AlertaRetraso.builder().minutosEstimados(10).build(),
                AlertaRetraso.builder().minutosEstimados(25).build());
        var votaciones = List.of(
                VotacionExpres.builder().estado(VotacionExpres.Estado.ABIERTA).build(),
                VotacionExpres.builder().estado(VotacionExpres.Estado.CERRADA)
                        .resultado(VotacionExpres.Opcion.REAGENDAR).build());

        var r = calcular(datos(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), retrasos,
                ausencias, votaciones));

        assertThat(r.imprevistos().ausencias()).isEqualTo(2);
        assertThat(r.imprevistos().ausenciasCriticas()).isEqualTo(1);
        assertThat(r.imprevistos().minutosRetrasoMedio()).isEqualTo(17.5);
        assertThat(r.imprevistos().votacionesAbiertas()).isEqualTo(1);
        assertThat(r.imprevistos().resultados().get(VotacionExpres.Opcion.REAGENDAR)).isEqualTo(1);
        assertThat(r.imprevistos().resultados().get(VotacionExpres.Opcion.CANCELAR)).isZero();
    }
}
