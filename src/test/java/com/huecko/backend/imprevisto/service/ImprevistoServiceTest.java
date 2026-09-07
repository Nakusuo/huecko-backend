package com.huecko.backend.imprevisto.service;

import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.imprevisto.dto.ImprevistoDtos;
import com.huecko.backend.mongo.document.VotacionExpres;
import com.huecko.backend.mongo.repository.VotacionExpresRepository;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.tiemporeal.NotificadorTiempoReal;
import com.huecko.backend.tiemporeal.dto.EventoTiempoReal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Módulo 5 con los repositorios simulados.
 *
 * Lo que más se vigila aquí es RF-18: qué pasa cuando nadie vota. Es el caso
 * que va a ocurrir de verdad —una hora de plazo, gente que no mira el móvil— y
 * el que peor avisa si se rompe, porque no lanza ninguna excepción.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImprevistoServiceTest {

    private static final UUID GRUPO = UUID.randomUUID();
    private static final UUID PLAN = UUID.randomUUID();

    @Mock private VotacionExpresRepository votacionRepository;
    @Mock private PlanRepository planRepository;
    @Mock private MiembroGrupoRepository miembroGrupoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private NotificadorTiempoReal notificador;

    private ImprevistoService servicio;
    private Usuario ana;
    private Usuario bruno;

    @BeforeEach
    void preparar() {
        servicio = new ImprevistoService(votacionRepository, planRepository,
                miembroGrupoRepository, usuarioRepository, new EvaluadorCriticidad(), notificador);
        ReflectionTestUtils.setField(servicio, "plazoMinutos", 60);
        ReflectionTestUtils.setField(servicio, "resultadoPorDefecto", VotacionExpres.Opcion.MANTENER);
        ReflectionTestUtils.setField(servicio, "diasPurga", 7);

        ana = Usuario.builder().id(UUID.randomUUID()).nombre("Ana").build();
        bruno = Usuario.builder().id(UUID.randomUUID()).nombre("Bruno").build();

        when(usuarioRepository.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(usuarioRepository.findById(bruno.getId())).thenReturn(Optional.of(bruno));
        when(miembroGrupoRepository.existsByGrupo_IdAndUsuario_Id(eq(GRUPO), any())).thenReturn(true);
        when(miembroGrupoRepository.countByGrupo_Id(GRUPO)).thenReturn(5L);
        when(votacionRepository.save(any())).thenAnswer(i -> {
            VotacionExpres v = i.getArgument(0);
            if (v.getId() == null) v.setId("v-1");
            return v;
        });
        when(votacionRepository.findByPlanIdAndEstado(any(), any())).thenReturn(Optional.empty());
        when(planRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    /* --- Reportar (RF-15, RF-16, RF-17, RF-19) --- */

    @Test
    @DisplayName("RF-17: la ausencia de quien propuso el plan abre votacion expres")
    void ausenciaCriticaAbreVotacion() {
        plan(Plan.Estado.CONFIRMADO, ana);
        soyMiembro(ana, false, MiembroGrupo.Rol.MIEMBRO);

        var r = servicio.reportar(ana.getId(), PLAN, "Me salió trabajo");

        assertThat(r.criticidad()).isEqualTo(VotacionExpres.Criticidad.CRITICA);
        assertThat(r.votacion()).isNotNull();
        assertThat(r.votacion().miembrosDelGrupo()).isEqualTo(5);
        verify(notificador).aGrupo(eq(GRUPO),
                eq(EventoTiempoReal.Tipo.VOTACION_EXPRES_ABIERTA), anyMap());
    }

    @Test
    @DisplayName("RF-19: una baja no critica solo se notifica, sin abrir votacion")
    void ausenciaNoCriticaSoloNotifica() {
        plan(Plan.Estado.CONFIRMADO, ana);
        soyMiembro(bruno, false, MiembroGrupo.Rol.MIEMBRO);

        var r = servicio.reportar(bruno.getId(), PLAN, null);

        assertThat(r.criticidad()).isEqualTo(VotacionExpres.Criticidad.NO_CRITICA);
        assertThat(r.votacion()).isNull();
        verify(notificador).aGrupo(eq(GRUPO),
                eq(EventoTiempoReal.Tipo.AUSENCIA_REPORTADA), anyMap());
        verify(votacionRepository, never()).save(any());
    }

    @Test
    @DisplayName("No se abren dos votaciones expres a la vez sobre el mismo plan")
    void noSeAbrenDosVotaciones() {
        plan(Plan.Estado.CONFIRMADO, ana);
        soyMiembro(ana, false, MiembroGrupo.Rol.MIEMBRO);
        when(votacionRepository.findByPlanIdAndEstado(PLAN.toString(), VotacionExpres.Estado.ABIERTA))
                .thenReturn(Optional.of(votacionAbierta(new LinkedHashMap<>())));

        assertThatThrownBy(() -> servicio.reportar(ana.getId(), PLAN, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("votación exprés abierta");
    }

    @Test
    @DisplayName("Un plan que aun se vota no admite imprevistos")
    void planNoConfirmado() {
        plan(Plan.Estado.PROPUESTO, ana);
        soyMiembro(ana, false, MiembroGrupo.Rol.MIEMBRO);

        assertThatThrownBy(() -> servicio.reportar(ana.getId(), PLAN, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("confirmado");
    }

    /* --- Cerrar (RF-17, RF-18) --- */

    @Test
    @DisplayName("RF-18: sin ningun voto se aplica el resultado por defecto y el plan sigue")
    void sinVotosSeAplicaElPorDefecto() {
        Plan plan = plan(Plan.Estado.CONFIRMADO, ana);
        votacionEnCurso(new LinkedHashMap<>());

        servicio.cerrar("v-1");

        assertThat(plan.getEstado()).isEqualTo(Plan.Estado.CONFIRMADO);
        VotacionExpres cerrada = capturarVotacionGuardada();
        assertThat(cerrada.getResultado()).isEqualTo(VotacionExpres.Opcion.MANTENER);
        assertThat(cerrada.isResultadoPorDefecto()).isTrue();
    }

    @Test
    @DisplayName("REAGENDAR deja el plan EN_RECOORDINACION, no cancelado")
    void reagendarVuelveACoordinacion() {
        Plan plan = plan(Plan.Estado.CONFIRMADO, ana);
        votacionEnCurso(votos(VotacionExpres.Opcion.REAGENDAR, VotacionExpres.Opcion.REAGENDAR,
                VotacionExpres.Opcion.MANTENER));

        servicio.cerrar("v-1");

        assertThat(plan.getEstado()).isEqualTo(Plan.Estado.EN_RECOORDINACION);
    }

    @Test
    @DisplayName("CANCELAR cancela el plan")
    void cancelarCancela() {
        Plan plan = plan(Plan.Estado.CONFIRMADO, ana);
        votacionEnCurso(votos(VotacionExpres.Opcion.CANCELAR, VotacionExpres.Opcion.CANCELAR));

        servicio.cerrar("v-1");

        assertThat(plan.getEstado()).isEqualTo(Plan.Estado.CANCELADO);
    }

    @Test
    @DisplayName("En empate gana la opcion mas conservadora: el plan sigue en pie")
    void elEmpateSeResuelveConservador() {
        Plan plan = plan(Plan.Estado.CONFIRMADO, ana);
        votacionEnCurso(votos(VotacionExpres.Opcion.CANCELAR, VotacionExpres.Opcion.MANTENER));

        servicio.cerrar("v-1");

        assertThat(plan.getEstado()).isEqualTo(Plan.Estado.CONFIRMADO);
        assertThat(capturarVotacionGuardada().getResultado())
                .isEqualTo(VotacionExpres.Opcion.MANTENER);
    }

    @Test
    @DisplayName("El TTL solo se activa al cerrar: una votacion abierta nunca se autoborra")
    void elTtlSoloSeMarcaAlCerrar() {
        plan(Plan.Estado.CONFIRMADO, ana);
        soyMiembro(ana, false, MiembroGrupo.Rol.MIEMBRO);

        servicio.reportar(ana.getId(), PLAN, null);
        assertThat(capturarVotacionGuardada().getPurgarEn())
                .as("mientras esta abierta, Mongo debe ignorarla")
                .isNull();

        votacionEnCurso(new LinkedHashMap<>());
        servicio.cerrar("v-1");
        assertThat(capturarVotacionGuardada().getPurgarEn()).isNotNull();
    }

    @Test
    @DisplayName("Cerrar dos veces no vuelve a tocar el plan")
    void cerrarDosVecesNoRepite() {
        plan(Plan.Estado.CONFIRMADO, ana);
        VotacionExpres ya = votacionAbierta(new LinkedHashMap<>());
        ya.setEstado(VotacionExpres.Estado.CERRADA);
        when(votacionRepository.findById("v-1")).thenReturn(Optional.of(ya));

        servicio.cerrar("v-1");

        verify(planRepository, never()).save(any());
    }

    /* --- Votar --- */

    @Test
    @DisplayName("Un voto fuera de plazo se rechaza aunque el barrido no haya pasado")
    void votoFueraDePlazo() {
        plan(Plan.Estado.CONFIRMADO, ana);
        VotacionExpres vencida = votacionAbierta(new LinkedHashMap<>());
        vencida.setExpiraEn(Instant.now().minus(5, ChronoUnit.MINUTES));
        when(votacionRepository.findByPlanIdAndEstado(PLAN.toString(), VotacionExpres.Estado.ABIERTA))
                .thenReturn(Optional.of(vencida));

        assertThatThrownBy(() -> servicio.votar(bruno.getId(), PLAN, VotacionExpres.Opcion.MANTENER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("venció");
    }

    @Test
    @DisplayName("Cambiar de opinion sustituye el voto, no suma otro")
    void cambiarDeOpinionSustituye() {
        plan(Plan.Estado.CONFIRMADO, ana);
        VotacionExpres abierta = votacionAbierta(new LinkedHashMap<>());
        when(votacionRepository.findByPlanIdAndEstado(PLAN.toString(), VotacionExpres.Estado.ABIERTA))
                .thenReturn(Optional.of(abierta));

        servicio.votar(bruno.getId(), PLAN, VotacionExpres.Opcion.CANCELAR);
        var r = servicio.votar(bruno.getId(), PLAN, VotacionExpres.Opcion.MANTENER);

        assertThat(r.votosEmitidos()).isEqualTo(1);
        assertThat(r.miVoto()).isEqualTo(VotacionExpres.Opcion.MANTENER);
    }

    /* ------------------------------------------------------------------ */

    private Plan plan(Plan.Estado estado, Usuario creador) {
        Plan plan = Plan.builder()
                .id(PLAN)
                .grupo(Grupo.builder().id(GRUPO).nombre("Los de siempre").build())
                .titulo("Cena")
                .estado(estado)
                .creadoPor(creador)
                .build();
        when(planRepository.findById(PLAN)).thenReturn(Optional.of(plan));
        return plan;
    }

    private void soyMiembro(Usuario usuario, boolean imprescindible, MiembroGrupo.Rol rol) {
        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, usuario.getId()))
                .thenReturn(Optional.of(MiembroGrupo.builder()
                        .rol(rol).esImprescindible(imprescindible).usuario(usuario).build()));
    }

    private VotacionExpres votacionAbierta(Map<String, VotacionExpres.Opcion> votos) {
        return VotacionExpres.builder()
                .id("v-1")
                .planId(PLAN.toString())
                .grupoId(GRUPO.toString())
                .usuarioReporta(ana.getId().toString())
                .nombreReporta("Ana")
                .criticidad(VotacionExpres.Criticidad.CRITICA)
                .estado(VotacionExpres.Estado.ABIERTA)
                .votos(votos)
                .miembrosDelGrupo(5)
                .abiertaEn(Instant.now())
                .expiraEn(Instant.now().plus(30, ChronoUnit.MINUTES))
                .build();
    }

    private void votacionEnCurso(Map<String, VotacionExpres.Opcion> votos) {
        when(votacionRepository.findById("v-1")).thenReturn(Optional.of(votacionAbierta(votos)));
    }

    private Map<String, VotacionExpres.Opcion> votos(VotacionExpres.Opcion... opciones) {
        Map<String, VotacionExpres.Opcion> votos = new LinkedHashMap<>();
        for (VotacionExpres.Opcion opcion : opciones) {
            votos.put(UUID.randomUUID().toString(), opcion);
        }
        return votos;
    }

    private VotacionExpres capturarVotacionGuardada() {
        ArgumentCaptor<VotacionExpres> captor = ArgumentCaptor.forClass(VotacionExpres.class);
        verify(votacionRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
