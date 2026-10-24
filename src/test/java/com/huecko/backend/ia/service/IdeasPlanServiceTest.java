package com.huecko.backend.ia.service;

import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.common.exception.ServicioNoDisponibleException;
import com.huecko.backend.grupo.dto.GrupoResponse;
import com.huecko.backend.grupo.dto.MiembroResponse;
import com.huecko.backend.grupo.service.GrupoService;
import com.huecko.backend.ia.ClienteIA;
import com.huecko.backend.ia.IaNoDisponibleException;
import com.huecko.backend.mongo.repository.LlamadaIARepository;
import com.huecko.backend.ia.dto.BorradorPlanResponse;
import com.huecko.backend.ia.dto.IdeasPlanResponse;
import com.huecko.backend.plan.dto.PlanResponse;
import com.huecko.backend.plan.service.PlanService;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** «Ideas con IA», traídas de la app móvil. */
@ExtendWith(MockitoExtension.class)
class IdeasPlanServiceTest {

    private static final UUID USUARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GRUPO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 22);
    private static final Instant AHORA = LocalDateTime.of(HOY, LocalTime.of(10, 0)).atZone(ZonaHoraria.ZONA).toInstant();
    private static final HuecosDelGrupo.Hueco VIERNES =
            new HuecosDelGrupo.Hueco(1, HOY.plusDays(1), LocalTime.of(12, 0), LocalTime.of(20, 0), 100, 3, 3);

    @Mock
    private GrupoService grupoService;
    @Mock
    private PlanService planService;
    @Mock
    private HuecosDelGrupo huecosDelGrupo;
    @Mock
    private ClienteIA clienteIA;
    @Mock
    private AccesoAsistente acceso;
    @Mock
    private LlamadaIARepository llamadas;

    private IdeasPlanService service() {
        return new IdeasPlanService(grupoService, planService, huecosDelGrupo, clienteIA, acceso,
                new RegistroUsoIA(llamadas), Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static PlanResponse plan(String titulo, int diasAtras) {
        return new PlanResponse(UUID.randomUUID(), GRUPO, titulo, null, USUARIO, AHORA, Plan.Estado.CONFIRMADO,
                true, List.of(), null, false, AHORA.minusSeconds(86_400L * diasAtras), null);
    }

    private void grupo(List<PlanResponse> planes) {
        when(grupoService.detalle(USUARIO, GRUPO)).thenReturn(new GrupoResponse(GRUPO, "Proyecto Integrador",
                "Curso de Full Stack", USUARIO, 80, AHORA, List.of(
                new MiembroResponse(USUARIO, "Alex", "alex@huecko.com", MiembroGrupo.Rol.ORGANIZADOR, true, null)), null));
        when(planService.listar(USUARIO, GRUPO)).thenReturn(planes);
        when(huecosDelGrupo.calcular(USUARIO, GRUPO, AHORA)).thenReturn(List.of(VIERNES));
    }

    @Test
    @DisplayName("Cada idea lleva las dos primeras horas de su hueco; un número inventado, ninguna")
    void ideasConSuHueco() {
        grupo(List.of());
        when(clienteIA.ideasPlan(any())).thenReturn(new ClienteIA.RespuestaIdeas(Arrays.asList(
                new ClienteIA.IdeaIA("Almuerzo del grupo", "COMIDA", "Cafetería", 1, "el viernes al mediodía están todos"),
                new ClienteIA.IdeaIA("Cine", "FIESTA", "  ", 9, "es fin de semana y apetece salir"),
                null,
                new ClienteIA.IdeaIA("  ", "OTRO", null, 1, "sin título no sirve"))));

        IdeasPlanResponse r = service().proponer(USUARIO, GRUPO);

        assertThat(r.ideas()).containsExactly(
                new IdeasPlanResponse.Idea("Almuerzo del grupo", "COMIDA", "Cafetería",
                        new BorradorPlanResponse.Ventana(VIERNES.fecha(), LocalTime.of(12, 0), LocalTime.of(14, 0), 100),
                        "el viernes al mediodía están todos"),
                new IdeasPlanResponse.Idea("Cine", "OTRO", null, null, "es fin de semana y apetece salir"));
        verify(acceso).exigir(USUARIO);
    }

    @Test
    @DisplayName("A la IA le llegan los cinco planes más recientes para que no los repita")
    void planesRecientes() {
        List<PlanResponse> planes = new ArrayList<>();
        for (int i = 7; i >= 1; i--) {
            planes.add(plan("Plan de hace " + i + " días", i));
        }
        grupo(planes);
        when(clienteIA.ideasPlan(any())).thenReturn(new ClienteIA.RespuestaIdeas(List.of(
                new ClienteIA.IdeaIA("Idea", "OTRO", null, null, "porque sí, y encaja bien"))));
        ArgumentCaptor<ClienteIA.PeticionIdeas> enviada = ArgumentCaptor.forClass(ClienteIA.PeticionIdeas.class);

        service().proponer(USUARIO, GRUPO);

        verify(clienteIA).ideasPlan(enviada.capture());
        assertThat(enviada.getValue().planesRecientes()).containsExactly(
                "Plan de hace 1 días", "Plan de hace 2 días", "Plan de hace 3 días", "Plan de hace 4 días", "Plan de hace 5 días");
        assertThat(enviada.getValue().descripcion()).isEqualTo("Curso de Full Stack");
        assertThat(enviada.getValue().huecos()).extracting(ClienteIA.HuecoIA::dia).containsExactly("viernes");
    }

    @Test
    @DisplayName("Si la IA no responde o no trae ninguna idea útil, 503")
    void sinIdeas() {
        grupo(List.of());
        when(clienteIA.ideasPlan(any()))
                .thenThrow(new IaNoDisponibleException("/v1/planes/ideas: 502", null))
                .thenReturn(new ClienteIA.RespuestaIdeas(List.of()));

        assertThatThrownBy(() -> service().proponer(USUARIO, GRUPO)).isInstanceOf(ServicioNoDisponibleException.class);
        assertThatThrownBy(() -> service().proponer(USUARIO, GRUPO)).isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    @DisplayName("Fuera del grupo: 404 sin gastar cupo")
    void ajeno() {
        when(grupoService.detalle(USUARIO, GRUPO)).thenThrow(new NotFoundException("Grupo no encontrado"));

        assertThatThrownBy(() -> service().proponer(USUARIO, GRUPO)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(acceso, clienteIA);
    }
}
