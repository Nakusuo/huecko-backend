package com.huecko.backend.plan.service;

import com.huecko.backend.common.exception.BusinessException;
import com.huecko.backend.common.exception.ForbiddenException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.grupo.dto.CeldaDisponibilidadResponse;
import com.huecko.backend.grupo.dto.DisponibilidadResponse;
import com.huecko.backend.grupo.service.GrupoService;
import com.huecko.backend.plan.dto.PlanRequests;
import com.huecko.backend.plan.dto.PlanResponse;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import com.huecko.backend.postgres.entity.VentanaPlan;
import com.huecko.backend.postgres.entity.VotoVentana;
import com.huecko.backend.postgres.repository.MiembroGrupoRepository;
import com.huecko.backend.postgres.repository.PlanRepository;
import com.huecko.backend.postgres.repository.UsuarioRepository;
import com.huecko.backend.postgres.repository.VotoVentanaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas del Módulo 3 con los repositorios simulados: qué se puede proponer,
 * quién puede votar y cuándo, y quién puede cerrar. Sin bases de datos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanServiceTest {

    private static final UUID GRUPO = UUID.randomUUID();
    private static final UUID PLAN = UUID.randomUUID();
    /** Miércoles. Se usa `plusWeeks` sobre él para no proponer fechas pasadas. */
    private static final LocalDate MIERCOLES = LocalDate.now().plusDays(7)
            .with(java.time.DayOfWeek.WEDNESDAY);

    @Mock private PlanRepository planRepository;
    @Mock private VotoVentanaRepository votoVentanaRepository;
    @Mock private MiembroGrupoRepository miembroGrupoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private GrupoService grupoService;

    private PlanService servicio;
    private Usuario ana;
    private Usuario bruno;

    @BeforeEach
    void preparar() {
        servicio = new PlanService(planRepository, votoVentanaRepository, miembroGrupoRepository,
                usuarioRepository, grupoService, new SelectorVentanaGanadora());
        ana = usuario("Ana");
        bruno = usuario("Bruno");

        when(usuarioRepository.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(usuarioRepository.findById(bruno.getId())).thenReturn(Optional.of(bruno));
        when(votoVentanaRepository.findByPlanId(any())).thenReturn(List.of());
        when(planRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    /* ------------------------------------------------------------------ *
     * Proponer (RF-08)
     * ------------------------------------------------------------------ */

    @Test
    @DisplayName("Un plan válido se guarda con sus ventanas y el porcentaje de cada una")
    void seProponeUnPlanValido() {
        soyMiembro(ana, MiembroGrupo.Rol.ORGANIZADOR);
        cruceCon(80, celda(3, 10, 100), celda(3, 11, 80), celda(3, 16, 100), celda(3, 17, 100));

        PlanResponse r = servicio.crear(ana.getId(), GRUPO, crear(
                ventana(MIERCOLES, "10:00", "12:00"),
                ventana(MIERCOLES, "16:00", "18:00")));

        assertThat(r.ventanas()).hasSize(2);
        // La de la mañana pasa por su peor hora, la de las 11 con un 80 %.
        assertThat(r.ventanas().get(0).disponibilidadPorcentaje()).isEqualTo(80);
        assertThat(r.ventanas().get(1).disponibilidadPorcentaje()).isEqualTo(100);
        assertThat(r.estado()).isEqualTo(Plan.Estado.PROPUESTO);
        assertThat(r.votacionAbierta()).isTrue();
    }

    @Test
    @DisplayName("RF-08: se rechaza una ventana que no llega al umbral del grupo")
    void seRechazaLaVentanaQueNoLlegaAlUmbral() {
        soyMiembro(ana, MiembroGrupo.Rol.ORGANIZADOR);
        cruceCon(80, celda(3, 10, 100), celda(3, 11, 40), celda(3, 16, 100), celda(3, 17, 100));

        assertThatThrownBy(() -> servicio.crear(ana.getId(), GRUPO, crear(
                ventana(MIERCOLES, "10:00", "12:00"),
                ventana(MIERCOLES, "16:00", "18:00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("40%");

        verify(planRepository, never()).save(any());
    }

    @Test
    @DisplayName("El umbral se exige en TODAS las horas, no solo en la primera")
    void elUmbralSeExigeEnTodaLaVentana() {
        soyMiembro(ana, MiembroGrupo.Rol.ORGANIZADOR);
        // La primera hora va sobrada; la tercera se hunde.
        cruceCon(80, celda(3, 10, 100), celda(3, 11, 100), celda(3, 12, 20),
                celda(3, 16, 100), celda(3, 17, 100));

        assertThatThrownBy(() -> servicio.crear(ana.getId(), GRUPO, crear(
                ventana(MIERCOLES, "10:00", "13:00"),
                ventana(MIERCOLES, "16:00", "18:00"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Una ventana que acaba en punto no exige la franja siguiente")
    void laVentanaQueAcabaEnPuntoNoTocaLaHoraSiguiente() {
        soyMiembro(ana, MiembroGrupo.Rol.ORGANIZADOR);
        // No se declara la celda de las 12: si el servicio la pidiera, fallaría.
        cruceCon(80, celda(3, 10, 100), celda(3, 11, 100), celda(3, 16, 100), celda(3, 17, 100));

        PlanResponse r = servicio.crear(ana.getId(), GRUPO, crear(
                ventana(MIERCOLES, "10:00", "12:00"),
                ventana(MIERCOLES, "16:00", "18:00")));

        assertThat(r.ventanas()).hasSize(2);
    }

    @Test
    @DisplayName("Una ventana fuera del horario que analiza Huecko se rechaza con un mensaje claro")
    void laVentanaFueraDeRangoSeRechaza() {
        soyMiembro(ana, MiembroGrupo.Rol.ORGANIZADOR);
        cruceCon(80, celda(3, 16, 100), celda(3, 17, 100));

        assertThatThrownBy(() -> servicio.crear(ana.getId(), GRUPO, crear(
                ventana(MIERCOLES, "05:00", "07:00"),
                ventana(MIERCOLES, "16:00", "18:00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("fuera del horario");
    }

    @Test
    @DisplayName("Un plazo que vence casi de inmediato se rechaza: nadie llegaría a votar")
    void elPlazoDemasiadoCortoSeRechaza() {
        soyMiembro(ana, MiembroGrupo.Rol.ORGANIZADOR);

        PlanRequests.Crear req = new PlanRequests.Crear(
                "Junta", null, Instant.now().plus(1, ChronoUnit.MINUTES), true,
                List.of(ventana(MIERCOLES, "10:00", "12:00"), ventana(MIERCOLES, "16:00", "18:00")));

        assertThatThrownBy(() -> servicio.crear(ana.getId(), GRUPO, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("minutos para votar");
    }

    @Test
    @DisplayName("Dos ventanas idénticas se rechazan: no habría nada que elegir entre ellas")
    void lasVentanasDuplicadasSeRechazan() {
        soyMiembro(ana, MiembroGrupo.Rol.ORGANIZADOR);
        cruceCon(80, celda(3, 10, 100), celda(3, 11, 100));

        assertThatThrownBy(() -> servicio.crear(ana.getId(), GRUPO, crear(
                ventana(MIERCOLES, "10:00", "12:00"),
                ventana(MIERCOLES, "10:00", "12:00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("idénticas");
    }

    @Test
    @DisplayName("Una ventana en fecha pasada se rechaza")
    void laVentanaEnFechaPasadaSeRechaza() {
        soyMiembro(ana, MiembroGrupo.Rol.ORGANIZADOR);
        cruceCon(80, celda(1, 10, 100), celda(1, 11, 100));

        assertThatThrownBy(() -> servicio.crear(ana.getId(), GRUPO, crear(
                ventana(LocalDate.now().minusDays(3), "10:00", "12:00"),
                ventana(MIERCOLES, "16:00", "18:00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("fecha pasada");
    }

    @Test
    @DisplayName("Quien no pertenece al grupo no puede proponer un plan en él")
    void elNoMiembroNoPropone() {
        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, ana.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(ana.getId(), GRUPO, crear(
                ventana(MIERCOLES, "10:00", "12:00"),
                ventana(MIERCOLES, "16:00", "18:00"))))
                .isInstanceOf(NotFoundException.class);
    }

    /* ------------------------------------------------------------------ *
     * Votar (RF-09)
     * ------------------------------------------------------------------ */

    @Test
    @DisplayName("Con voto múltiple se pueden marcar varias ventanas")
    void conVotoMultipleSeAcumula() {
        Plan plan = planAbierto(true);
        planExiste(plan, bruno);
        when(votoVentanaRepository.findByPlanIdAndUsuarioId(PLAN, bruno.getId())).thenReturn(List.of());

        servicio.votar(bruno.getId(), PLAN, plan.getVentanas().get(0).getId());

        verify(votoVentanaRepository, never()).deleteAll(any());
        verify(votoVentanaRepository).save(any());
    }

    @Test
    @DisplayName("Con voto único, elegir otra opción sustituye a la anterior en vez de fallar")
    void conVotoUnicoElNuevoVotoSustituyeAlViejo() {
        Plan plan = planAbierto(false);
        planExiste(plan, bruno);

        VotoVentana anterior = VotoVentana.builder()
                .ventana(plan.getVentanas().get(0)).usuario(bruno).build();
        when(votoVentanaRepository.findByPlanIdAndUsuarioId(PLAN, bruno.getId()))
                .thenReturn(List.of(anterior));

        servicio.votar(bruno.getId(), PLAN, plan.getVentanas().get(1).getId());

        verify(votoVentanaRepository).deleteAll(List.of(anterior));

        ArgumentCaptor<VotoVentana> nuevo = ArgumentCaptor.forClass(VotoVentana.class);
        verify(votoVentanaRepository).save(nuevo.capture());
        assertThat(nuevo.getValue().getVentana().getId()).isEqualTo(plan.getVentanas().get(1).getId());
    }

    @Test
    @DisplayName("No se puede votar una ventana de otro plan")
    void noSeVotaUnaVentanaAjena() {
        Plan plan = planAbierto(true);
        planExiste(plan, bruno);

        assertThatThrownBy(() -> servicio.votar(bruno.getId(), PLAN, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("RF-09: con el plazo vencido ya no se aceptan votos")
    void conElPlazoVencidoNoSeVota() {
        Plan plan = planAbierto(true);
        plan.setPlazoVotacion(Instant.now().minusSeconds(60));
        planExiste(plan, bruno);

        assertThatThrownBy(() -> servicio.votar(bruno.getId(), PLAN, plan.getVentanas().get(0).getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("plazo");

        verify(votoVentanaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sobre un plan ya confirmado tampoco se vota")
    void enUnPlanCerradoNoSeVota() {
        Plan plan = planAbierto(true);
        plan.setEstado(Plan.Estado.CONFIRMADO);
        planExiste(plan, bruno);

        assertThatThrownBy(() -> servicio.votar(bruno.getId(), PLAN, plan.getVentanas().get(0).getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cerrada");
    }

    @Test
    @DisplayName("Un plan de un grupo ajeno se responde como inexistente")
    void elPlanAjenoNoSeVe() {
        Plan plan = planAbierto(true);
        when(planRepository.findByIdConVentanas(PLAN)).thenReturn(Optional.of(plan));
        when(miembroGrupoRepository.existsByGrupo_IdAndUsuario_Id(GRUPO, bruno.getId())).thenReturn(false);

        assertThatThrownBy(() -> servicio.detalle(bruno.getId(), PLAN))
                .isInstanceOf(NotFoundException.class);
    }

    /* ------------------------------------------------------------------ *
     * Cerrar (RF-10)
     * ------------------------------------------------------------------ */

    @Test
    @DisplayName("RF-10: al cerrar se confirma la ventana más votada")
    void alCerrarSeConfirmaLaGanadora() {
        Plan plan = planAbierto(true);
        planExiste(plan, ana);
        soyMiembro(ana, MiembroGrupo.Rol.MIEMBRO);

        VentanaPlan ganadora = plan.getVentanas().get(1);
        when(votoVentanaRepository.findByPlanId(PLAN)).thenReturn(List.of(
                VotoVentana.builder().ventana(plan.getVentanas().get(0)).usuario(bruno).build(),
                VotoVentana.builder().ventana(ganadora).usuario(ana).build(),
                VotoVentana.builder().ventana(ganadora).usuario(usuario("Carla")).build()));

        // Ana creó el plan, así que puede cerrarlo aunque no sea organizadora.
        PlanResponse r = servicio.cerrarManualmente(ana.getId(), PLAN);

        assertThat(r.estado()).isEqualTo(Plan.Estado.CONFIRMADO);
        assertThat(r.ventanaConfirmadaId()).isEqualTo(ganadora.getId());
        assertThat(r.cerradoEn()).isNotNull();
        assertThat(r.votacionAbierta()).isFalse();
    }

    @Test
    @DisplayName("Una votación que cierra sin votos cancela el plan en vez de confirmar uno al azar")
    void sinVotosElPlanSeCancela() {
        Plan plan = planAbierto(true);
        planExiste(plan, ana);
        soyMiembro(ana, MiembroGrupo.Rol.MIEMBRO);
        when(votoVentanaRepository.findByPlanId(PLAN)).thenReturn(List.of());

        PlanResponse r = servicio.cerrarManualmente(ana.getId(), PLAN);

        assertThat(r.estado()).isEqualTo(Plan.Estado.CANCELADO);
        assertThat(r.ventanaConfirmadaId()).isNull();
    }

    @Test
    @DisplayName("Un miembro cualquiera no puede cortar la votación cuando su opción va ganando")
    void elMiembroRasoNoCierraLaVotacionAjena() {
        Plan plan = planAbierto(true); // creado por Ana
        planExiste(plan, bruno);
        soyMiembro(bruno, MiembroGrupo.Rol.MIEMBRO);

        assertThatThrownBy(() -> servicio.cerrarManualmente(bruno.getId(), PLAN))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("El organizador del grupo sí puede cerrar un plan que no propuso")
    void elOrganizadorDelGrupoCierraCualquierPlan() {
        Plan plan = planAbierto(true); // creado por Ana
        planExiste(plan, bruno);
        soyMiembro(bruno, MiembroGrupo.Rol.ORGANIZADOR);
        when(votoVentanaRepository.findByPlanId(PLAN)).thenReturn(List.of(
                VotoVentana.builder().ventana(plan.getVentanas().get(0)).usuario(bruno).build()));

        assertThat(servicio.cerrarManualmente(bruno.getId(), PLAN).estado())
                .isEqualTo(Plan.Estado.CONFIRMADO);
    }

    @Test
    @DisplayName("Cerrar dos veces se rechaza en vez de reescribir el resultado")
    void noSeCierraDosVeces() {
        Plan plan = planAbierto(true);
        plan.setEstado(Plan.Estado.CONFIRMADO);
        planExiste(plan, ana);
        soyMiembro(ana, MiembroGrupo.Rol.ORGANIZADOR);

        assertThatThrownBy(() -> servicio.cerrarManualmente(ana.getId(), PLAN))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("El cierre automático ignora un plan que ya se cerró a mano entre medias")
    void elCierreAutomaticoNoPisaUnCierreManual() {
        Plan plan = planAbierto(true);
        plan.setEstado(Plan.Estado.CONFIRMADO);
        when(planRepository.findByIdConVentanas(PLAN)).thenReturn(Optional.of(plan));

        servicio.cerrarPorPlazoVencido(PLAN);

        verify(planRepository, never()).save(any());
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
                .creadoPor(ana)
                .build();
    }

    private void soyMiembro(Usuario usuario, MiembroGrupo.Rol rol) {
        when(miembroGrupoRepository.findByGrupo_IdAndUsuario_Id(GRUPO, usuario.getId()))
                .thenReturn(Optional.of(MiembroGrupo.builder()
                        .grupo(grupo()).usuario(usuario).rol(rol).esImprescindible(false).build()));
    }

    private void planExiste(Plan plan, Usuario quienPregunta) {
        when(planRepository.findByIdConVentanas(PLAN)).thenReturn(Optional.of(plan));
        when(miembroGrupoRepository.existsByGrupo_IdAndUsuario_Id(GRUPO, quienPregunta.getId()))
                .thenReturn(true);
    }

    /** Plan abierto, creado por Ana, con dos ventanas el miércoles. */
    private Plan planAbierto(boolean votosMultiples) {
        Plan plan = Plan.builder()
                .id(PLAN)
                .grupo(grupo())
                .titulo("Junta de avance")
                .creadoPor(ana)
                .plazoVotacion(Instant.now().plus(2, ChronoUnit.HOURS))
                .estado(Plan.Estado.PROPUESTO)
                .votosMultiples(votosMultiples)
                .creadoEn(Instant.now())
                .build();

        List<VentanaPlan> ventanas = new ArrayList<>(List.of(
                VentanaPlan.builder().id(UUID.randomUUID()).plan(plan).fecha(MIERCOLES)
                        .horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(12, 0))
                        .disponibilidadPorcentaje(100).build(),
                VentanaPlan.builder().id(UUID.randomUUID()).plan(plan).fecha(MIERCOLES)
                        .horaInicio(LocalTime.of(16, 0)).horaFin(LocalTime.of(18, 0))
                        .disponibilidadPorcentaje(90).build()));
        plan.setVentanas(ventanas);
        return plan;
    }

    private PlanRequests.Ventana ventana(LocalDate fecha, String inicio, String fin) {
        return new PlanRequests.Ventana(fecha, LocalTime.parse(inicio), LocalTime.parse(fin));
    }

    private PlanRequests.Crear crear(PlanRequests.Ventana... ventanas) {
        return new PlanRequests.Crear("Junta de avance", "Biblioteca",
                Instant.now().plus(24, ChronoUnit.HOURS), true, List.of(ventanas));
    }

    private CeldaDisponibilidadResponse celda(int dia, int hora, int porcentaje) {
        return new CeldaDisponibilidadResponse(dia, hora, porcentaje / 25, 4, porcentaje, porcentaje >= 80);
    }

    /** El cruce que devolverá `grupoService` para cualquier semana que se le pida. */
    private void cruceCon(int umbral, CeldaDisponibilidadResponse... celdas) {
        when(grupoService.disponibilidad(any(), any(), any(), any())).thenReturn(
                new DisponibilidadResponse(GRUPO, umbral, 4,
                        LocalDate.now(), LocalDate.now().plusDays(6), 8, 20,
                        List.of(celdas), List.of()));
    }
}
