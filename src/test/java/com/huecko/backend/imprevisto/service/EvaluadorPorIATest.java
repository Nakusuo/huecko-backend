package com.huecko.backend.imprevisto.service;

import com.huecko.backend.ia.ClienteIA;
import com.huecko.backend.ia.IaNoDisponibleException;
import com.huecko.backend.imprevisto.service.EvaluadorCriticidad.Origen;
import com.huecko.backend.mongo.document.VotacionExpres.Criticidad;
import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Lo propio del evaluador con IA; lo común está en {@link EvaluadorCriticidadContractTest}. */
class EvaluadorPorIATest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();

    private final ClienteIA ia = mock(ClienteIA.class);
    private final EvaluadorPorIA evaluador = new EvaluadorPorIA(ia);

    @Test
    void unMiembroQueLlevaLasEntradasEsCriticoPorIA() {
        when(ia.criticidad(any())).thenReturn(
                new ClienteIA.RespuestaCriticidad("CRITICA", "tiene las entradas de todo el grupo"));

        var v = evaluador.evaluar(plan(), miembro(MiembroGrupo.Rol.MIEMBRO), BRUNO, "Tengo las entradas");

        assertThat(v.criticidad()).isEqualTo(Criticidad.CRITICA);
        assertThat(v.razon()).isEqualTo("tiene las entradas de todo el grupo");
        assertThat(v.origen()).isEqualTo(Origen.IA);
    }

    @Test
    void mandaAlServicioElPlanElRolYElMotivo() {
        when(ia.criticidad(any())).thenReturn(
                new ClienteIA.RespuestaCriticidad("NO_CRITICA", "solo llegará un poco tarde"));
        var peticion = ArgumentCaptor.forClass(ClienteIA.PeticionCriticidad.class);

        evaluador.evaluar(plan(), miembro(MiembroGrupo.Rol.ORGANIZADOR), BRUNO, "Llego tarde");

        verify(ia).criticidad(peticion.capture());
        assertThat(peticion.getValue()).isEqualTo(
                new ClienteIA.PeticionCriticidad("Cena", "Casa de Ana", "ORGANIZADOR", "Llego tarde"));
    }

    @Test
    void sinMotivoDecidenLasReglasSinLlamarAlServicio() {
        var v = evaluador.evaluar(plan(), miembro(MiembroGrupo.Rol.ORGANIZADOR), BRUNO, null);

        assertThat(v.origen()).isEqualTo(Origen.REGLAS);
        assertThat(v.esCritica()).isTrue();
        verifyNoInteractions(ia);
    }

    @Test
    void quienProponeElPlanNoPasaPorElModelo() {
        var v = evaluador.evaluar(plan(), miembro(MiembroGrupo.Rol.MIEMBRO), ANA, "No puedo ir");

        assertThat(v.origen()).isEqualTo(Origen.REGLAS);
        verifyNoInteractions(ia);
    }

    @Test
    void siElServicioFallaRespondenLasReglasYLoDice() {
        when(ia.criticidad(any())).thenThrow(new IaNoDisponibleException("tiempo agotado", null));

        var v = evaluador.evaluar(plan(), miembro(MiembroGrupo.Rol.ORGANIZADOR), BRUNO, "No puedo ir");

        assertThat(v.origen()).isEqualTo(Origen.REGLAS_POR_FALLO);
        assertThat(v.razon()).isEqualTo("es organizador del grupo");
        assertThat(v.esCritica()).isTrue();
    }

    @Test
    void unaRespuestaInservibleCuentaComoFallo() {
        when(ia.criticidad(any())).thenReturn(new ClienteIA.RespuestaCriticidad("QUIZAS", "no sé"));

        var v = evaluador.evaluar(plan(), miembro(MiembroGrupo.Rol.MIEMBRO), BRUNO, "No puedo ir");

        assertThat(v.origen()).isEqualTo(Origen.REGLAS_POR_FALLO);
        assertThat(v.esCritica()).isFalse();
    }

    private static Plan plan() {
        return Plan.builder().id(UUID.randomUUID()).titulo("Cena").lugar("Casa de Ana")
                .grupo(Grupo.builder().id(UUID.randomUUID()).nombre("G").build())
                .creadoPor(Usuario.builder().id(ANA).nombre("Ana").build())
                .build();
    }

    private static MiembroGrupo miembro(MiembroGrupo.Rol rol) {
        return MiembroGrupo.builder().rol(rol).esImprescindible(false).build();
    }
}
