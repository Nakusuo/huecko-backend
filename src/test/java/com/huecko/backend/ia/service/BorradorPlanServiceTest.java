package com.huecko.backend.ia.service;

import com.huecko.backend.common.ZonaHoraria;
import com.huecko.backend.common.exception.DemasiadosIntentosException;
import com.huecko.backend.common.exception.NotFoundException;
import com.huecko.backend.common.exception.ServicioNoDisponibleException;
import com.huecko.backend.grupo.dto.GrupoResponse;
import com.huecko.backend.grupo.dto.MiembroResponse;
import com.huecko.backend.grupo.service.GrupoService;
import com.huecko.backend.ia.ClienteIA;
import com.huecko.backend.ia.IaNoDisponibleException;
import com.huecko.backend.mongo.repository.LlamadaIARepository;
import com.huecko.backend.ia.dto.BorradorPlanResponse;
import com.huecko.backend.postgres.entity.MiembroGrupo;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Borrador de plan desde una frase, traído de la app móvil. */
@ExtendWith(MockitoExtension.class)
class BorradorPlanServiceTest {

    private static final UUID USUARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GRUPO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    /** Jueves 22 de octubre de 2026, 10:00 en la zona de los grupos. */
    private static final LocalDate HOY = LocalDate.of(2026, 10, 22);
    private static final Instant AHORA = instante(HOY, 10, 0);

    private static final HuecosDelGrupo.Hueco VIERNES = hueco(1, HOY.plusDays(1), 12, 20, 100);
    private static final HuecosDelGrupo.Hueco LUNES = hueco(2, LocalDate.of(2026, 10, 26), 8, 11, 67);

    @Mock
    private GrupoService grupoService;
    @Mock
    private HuecosDelGrupo huecosDelGrupo;
    @Mock
    private ClienteIA clienteIA;
    @Mock
    private AccesoAsistente acceso;
    @Mock
    private LlamadaIARepository llamadas;

    private BorradorPlanService service() {
        return new BorradorPlanService(grupoService, huecosDelGrupo, clienteIA, acceso,
                new RegistroUsoIA(llamadas), Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static Instant instante(LocalDate fecha, int hora, int minuto) {
        return LocalDateTime.of(fecha, LocalTime.of(hora, minuto)).atZone(ZonaHoraria.ZONA).toInstant();
    }

    private static HuecosDelGrupo.Hueco hueco(int numero, LocalDate fecha, int desde, int hasta, int porcentaje) {
        return new HuecosDelGrupo.Hueco(numero, fecha, LocalTime.of(desde, 0), LocalTime.of(hasta, 0), porcentaje, 2, 3);
    }

    private static ClienteIA.OpcionIA opcion(Integer numero, String inicio, String fin) {
        return new ClienteIA.OpcionIA(numero, inicio, fin);
    }

    private void grupoConHuecos() {
        when(grupoService.detalle(USUARIO, GRUPO)).thenReturn(new GrupoResponse(GRUPO, "Proyecto Integrador", "",
                USUARIO, 80, AHORA, List.of(
                new MiembroResponse(USUARIO, "Alex", "alex@huecko.com", MiembroGrupo.Rol.ORGANIZADOR, true),
                new MiembroResponse(UUID.randomUUID(), "Ana", "ana@huecko.com", MiembroGrupo.Rol.MIEMBRO, false),
                new MiembroResponse(UUID.randomUUID(), "Luis", "luis@huecko.com", MiembroGrupo.Rol.MIEMBRO, false)), null));
        when(huecosDelGrupo.calcular(USUARIO, GRUPO, AHORA)).thenReturn(List.of(VIERNES, LUNES));
    }

    @Test
    @DisplayName("Arma el borrador con las horas que pidió la IA, dentro de huecos reales")
    void borradorCompleto() {
        grupoConHuecos();
        when(clienteIA.borradorPlan(any())).thenReturn(new ClienteIA.RespuestaBorrador(
                "Repaso de Cálculo", "ESTUDIO", "Biblioteca",
                List.of(opcion(1, "15:00", "17:00"), opcion(1, "17:00", "19:00")), 12));

        BorradorPlanResponse r = service().redactar(USUARIO, GRUPO, "  repaso de 2 horas el viernes en la tarde ");

        assertThat(r.titulo()).isEqualTo("Repaso de Cálculo");
        assertThat(r.categoria()).isEqualTo("ESTUDIO");
        assertThat(r.lugar()).isEqualTo("Biblioteca");
        assertThat(r.ventanas()).containsExactly(
                new BorradorPlanResponse.Ventana(VIERNES.fecha(), LocalTime.of(15, 0), LocalTime.of(17, 0), 100),
                new BorradorPlanResponse.Ventana(VIERNES.fecha(), LocalTime.of(17, 0), LocalTime.of(19, 0), 100));
        // 12 horas desde las 10:00 son las 22:00 de hoy: antes del viernes 15:00.
        assertThat(r.plazoVotacion()).isEqualTo(instante(HOY, 22, 0));
        verify(acceso).exigir(USUARIO);
    }

    @Test
    @DisplayName("A la IA le llega la frase, el grupo, la fecha de hoy y los huecos numerados con su día")
    void loQueVeLaIA() {
        grupoConHuecos();
        when(clienteIA.borradorPlan(any())).thenReturn(new ClienteIA.RespuestaBorrador("Plan", "OTRO", null, List.of(), 48));
        ArgumentCaptor<ClienteIA.PeticionBorrador> enviada = ArgumentCaptor.forClass(ClienteIA.PeticionBorrador.class);

        service().redactar(USUARIO, GRUPO, "  cena el viernes ");

        verify(clienteIA).borradorPlan(enviada.capture());
        ClienteIA.PeticionBorrador p = enviada.getValue();
        assertThat(p.texto()).isEqualTo("cena el viernes");
        assertThat(p.grupo()).isEqualTo("Proyecto Integrador");
        assertThat(p.integrantes()).isEqualTo(3);
        assertThat(p.ahora()).isEqualTo("jueves 22 de octubre de 2026, 10:00");
        assertThat(p.huecos()).containsExactly(
                new ClienteIA.HuecoIA(1, "2026-10-23", "viernes", "12:00", "20:00", 100, 2, 3),
                new ClienteIA.HuecoIA(2, "2026-10-26", "lunes", "08:00", "11:00", 67, 2, 3));
    }

    @Test
    @DisplayName("Un número que no está en la lista se descarta: la IA no inventa momentos")
    void numeroInventado() {
        var ventanas = BorradorPlanService.ventanas(List.of(VIERNES, LUNES),
                Arrays.asList(opcion(7, "10:00", "12:00"), null, opcion(null, "10:00", "12:00"), opcion(2, "08:00", "10:00")));

        assertThat(ventanas).extracting(BorradorPlanResponse.Ventana::fecha).containsExactly(LUNES.fecha());
    }

    @Test
    @DisplayName("Horas fuera del hueco, al revés o mal escritas: las primeras dos horas del hueco")
    void horasInvalidas() {
        for (String[] horas : new String[][]{{"07:00", "09:00"}, {"18:00", "21:00"}, {"16:00", "15:00"}, {"4pm", "6pm"}, {null, null}}) {
            var tramo = BorradorPlanService.tramo(VIERNES, horas[0], horas[1]);
            assertThat(tramo.horaInicio()).as(Arrays.toString(horas)).isEqualTo(LocalTime.of(12, 0));
            assertThat(tramo.horaFin()).as(Arrays.toString(horas)).isEqualTo(LocalTime.of(14, 0));
        }
        // Un hueco de una hora se usa entero.
        var corto = BorradorPlanService.tramo(hueco(3, HOY.plusDays(2), 18, 19, 100), null, null);
        assertThat(corto.horaFin()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    @DisplayName("Sin solapes ni repetidas, y como mucho tres opciones")
    void sinSolapes() {
        var ventanas = BorradorPlanService.ventanas(List.of(VIERNES, LUNES), List.of(
                opcion(1, "12:00", "14:00"),
                opcion(1, "13:00", "15:00"),   // pisa la anterior
                opcion(1, "12:00", "14:00"),   // repetida
                opcion(1, "14:00", "16:00"),
                opcion(2, "08:00", "10:00"),
                opcion(1, "16:00", "18:00"))); // ya hay tres

        assertThat(ventanas).extracting(BorradorPlanResponse.Ventana::horaInicio)
                .containsExactly(LocalTime.of(12, 0), LocalTime.of(14, 0), LocalTime.of(8, 0));
    }

    @Test
    @DisplayName("El plazo va en punto y nunca cierra a menos de una hora de la primera opción")
    void plazo() {
        var viernes15 = List.of(new BorradorPlanResponse.Ventana(VIERNES.fecha(), LocalTime.of(15, 0), LocalTime.of(17, 0), 100));
        Instant diezYCuarto = instante(HOY, 10, 15);

        // 48 h por defecto acabarían el sábado: se adelanta al viernes 14:00.
        assertThat(BorradorPlanService.plazo(AHORA, null, viernes15)).isEqualTo(instante(HOY.plusDays(1), 14, 0));
        assertThat(BorradorPlanService.plazo(AHORA, 500, viernes15)).isEqualTo(instante(HOY.plusDays(1), 14, 0));
        // 3 horas desde las 10:15 se redondean a las 14:00.
        assertThat(BorradorPlanService.plazo(diezYCuarto, 3, viernes15)).isEqualTo(instante(HOY, 14, 0));
        // Sin opciones, solo las horas sugeridas.
        assertThat(BorradorPlanService.plazo(AHORA, 24, List.of())).isEqualTo(instante(HOY.plusDays(1), 10, 0));
    }

    @Test
    @DisplayName("Categoría desconocida pasa a OTRO; título y lugar se recortan")
    void limpiaLoAccesorio() {
        grupoConHuecos();
        when(clienteIA.borradorPlan(any())).thenReturn(new ClienteIA.RespuestaBorrador(
                "t".repeat(200), "FIESTA", "   ", null, null));

        BorradorPlanResponse r = service().redactar(USUARIO, GRUPO, "fiesta");

        assertThat(r.titulo()).hasSize(120);
        assertThat(r.categoria()).isEqualTo("OTRO");
        assertThat(r.lugar()).isNull();
        assertThat(r.ventanas()).isEmpty();
    }

    @Test
    @DisplayName("Si la IA no responde, 503")
    void iaCaida() {
        grupoConHuecos();
        when(clienteIA.borradorPlan(any())).thenThrow(new IaNoDisponibleException("/v1/planes/borrador: 502", null));

        assertThatThrownBy(() -> service().redactar(USUARIO, GRUPO, "cena")).isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    @DisplayName("Fuera del grupo: 404 sin gastar cupo ni llamar a la IA")
    void ajenoAlGrupo() {
        when(grupoService.detalle(USUARIO, GRUPO)).thenThrow(new NotFoundException("Grupo no encontrado"));

        assertThatThrownBy(() -> service().redactar(USUARIO, GRUPO, "cena")).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(acceso, clienteIA);
    }

    @Test
    @DisplayName("Sin cupo no se llama a la IA")
    void sinCupo() {
        grupoConHuecos();
        doThrow(new DemasiadosIntentosException("Usaste mucho la IA")).when(acceso).exigir(USUARIO);

        assertThatThrownBy(() -> service().redactar(USUARIO, GRUPO, "cena")).isInstanceOf(DemasiadosIntentosException.class);
        verify(clienteIA, never()).borradorPlan(any());
    }
}
