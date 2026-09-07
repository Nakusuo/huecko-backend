package com.huecko.backend.imprevisto.service;

import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF-16. Son las reglas que deciden si una ausencia interrumpe el plan del
 * grupo o solo se anota, así que equivocarse aquí molesta en las dos
 * direcciones: o se abre una votación por cada baja, o no se abre cuando falta
 * quien traía las llaves.
 */
class EvaluadorCriticidadTest {

    private final EvaluadorCriticidad evaluador = new EvaluadorCriticidad();

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();

    @Test
    @DisplayName("Quien propuso el plan es critico aunque sea miembro raso")
    void elCreadorEsCritico() {
        var veredicto = evaluador.evaluar(planCreadoPor(ANA), miembro(false, MiembroGrupo.Rol.MIEMBRO), ANA);

        assertThat(veredicto.esCritica()).isTrue();
        assertThat(veredicto.razon()).contains("propuso");
    }

    @Test
    @DisplayName("Quien esta marcado como imprescindible es critico")
    void elImprescindibleEsCritico() {
        var veredicto = evaluador.evaluar(planCreadoPor(ANA), miembro(true, MiembroGrupo.Rol.MIEMBRO), BRUNO);

        assertThat(veredicto.esCritica()).isTrue();
        assertThat(veredicto.razon()).contains("imprescindible");
    }

    @Test
    @DisplayName("Un organizador del grupo es critico")
    void elOrganizadorEsCritico() {
        var veredicto = evaluador.evaluar(planCreadoPor(ANA),
                miembro(false, MiembroGrupo.Rol.ORGANIZADOR), BRUNO);

        assertThat(veredicto.esCritica()).isTrue();
        assertThat(veredicto.razon()).contains("organizador");
    }

    @Test
    @DisplayName("RF-19: un miembro raso que no es imprescindible NO es critico")
    void elMiembroRasoNoEsCritico() {
        var veredicto = evaluador.evaluar(planCreadoPor(ANA),
                miembro(false, MiembroGrupo.Rol.MIEMBRO), BRUNO);

        assertThat(veredicto.esCritica()).isFalse();
    }

    @Test
    @DisplayName("Un plan sin creador registrado no revienta la evaluacion")
    void planSinCreador() {
        Plan plan = Plan.builder().id(UUID.randomUUID()).titulo("Cena").build();

        var veredicto = evaluador.evaluar(plan, miembro(false, MiembroGrupo.Rol.MIEMBRO), ANA);

        assertThat(veredicto.esCritica()).isFalse();
    }

    /* ------------------------------------------------------------------ */

    private Plan planCreadoPor(UUID usuarioId) {
        return Plan.builder()
                .id(UUID.randomUUID())
                .titulo("Cena")
                .creadoPor(Usuario.builder().id(usuarioId).nombre("Quien sea").build())
                .build();
    }

    private MiembroGrupo miembro(boolean imprescindible, MiembroGrupo.Rol rol) {
        return MiembroGrupo.builder().rol(rol).esImprescindible(imprescindible).build();
    }
}
