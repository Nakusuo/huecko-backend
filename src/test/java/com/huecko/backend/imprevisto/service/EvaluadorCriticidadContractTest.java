package com.huecko.backend.imprevisto.service;

import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import com.huecko.backend.postgres.entity.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * El contrato de RF-16, contra <b>todas</b> las implementaciones.
 *
 * <p>Existe por una razón concreta: el documento deja «un LLM como mejora de
 * fase 2» para la evaluación de criticidad. Cuando llegue, hará falta poder
 * responder a «¿se comporta como debe?» sin leerse el prompt.
 *
 * <p>Estas pruebas son lo que <b>cualquier</b> evaluador tiene que cumplir, sea
 * de reglas o de modelo. <b>Una implementación nueva se añade a
 * {@link #implementaciones()} y no entra si no pasa.</b>
 *
 * <p>Ojo con lo que NO se comprueba aquí: el resultado exacto en los casos
 * ambiguos. Un LLM puede clasificar distinto que las reglas en un caso dudoso y
 * seguir siendo correcto; lo que no puede es dejar sin razón un veredicto, ni
 * reventar, ni marcar como prescindible a quien propuso el plan.
 *
 * <p>Las particularidades de las reglas —el orden de los tres motivos, el texto
 * concreto— se prueban en {@link EvaluadorPorReglasTest}.
 */
class EvaluadorCriticidadContractTest {

    /** Al añadir una implementación, se añade aquí. */
    static Stream<EvaluadorCriticidad> implementaciones() {
        return Stream.of(new EvaluadorPorReglas());
    }

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();

    @ParameterizedTest(name = "{0}")
    @MethodSource("implementaciones")
    @DisplayName("Contrato 1: siempre devuelve un veredicto, nunca nulo ni excepción")
    void siempreDevuelveVeredicto(EvaluadorCriticidad evaluador) {
        for (Object[] caso : casos()) {
            Plan plan = (Plan) caso[0];
            MiembroGrupo miembro = (MiembroGrupo) caso[1];
            UUID quien = (UUID) caso[2];

            assertThatCode(() -> {
                var v = evaluador.evaluar(plan, miembro, quien);
                assertThat(v).isNotNull();
                assertThat(v.criticidad()).isNotNull();
            }).as("caso %s", caso[3]).doesNotThrowAnyException();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("implementaciones")
    @DisplayName("Contrato 2: siempre explica por qué, con una razón legible")
    void siempreExplica(EvaluadorCriticidad evaluador) {
        for (Object[] caso : casos()) {
            var v = evaluador.evaluar((Plan) caso[0], (MiembroGrupo) caso[1], (UUID) caso[2]);

            assertThat(v.razon())
                    .as("caso %s", caso[3])
                    .isNotNull()
                    .isNotBlank();

            // Se muestra tal cual en la cabecera del panel, detrás de «Se abrió
            // esta votación porque …». Una razón de tres letras o una constante
            // en mayúsculas no sirve ahí.
            assertThat(v.razon().length()).as("caso %s: razón demasiado corta", caso[3])
                    .isGreaterThan(8);
            assertThat(v.razon()).as("caso %s: la razón parece jerga de sistema", caso[3])
                    .isNotEqualTo(v.razon().toUpperCase());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("implementaciones")
    @DisplayName("Contrato 3: siempre dice de dónde salió el veredicto")
    void siempreDiceSuOrigen(EvaluadorCriticidad evaluador) {
        for (Object[] caso : casos()) {
            var v = evaluador.evaluar((Plan) caso[0], (MiembroGrupo) caso[1], (UUID) caso[2]);
            assertThat(v.origen()).as("caso %s", caso[3]).isNotNull();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("implementaciones")
    @DisplayName("Contrato 4: quien propuso el plan es SIEMPRE crítico")
    void elCreadorNuncaEsPrescindible(EvaluadorCriticidad evaluador) {
        // Es el único resultado que no admite matices, sea con reglas o con
        // modelo: si quien organizó la quedada no va, el grupo tiene que
        // decidir. Un evaluador que diga lo contrario está roto.
        var v = evaluador.evaluar(planCreadoPor(ANA), miembro(false, MiembroGrupo.Rol.MIEMBRO), ANA);

        assertThat(v.esCritica()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("implementaciones")
    @DisplayName("Contrato 5: quien el grupo marcó imprescindible es SIEMPRE crítico")
    void elImprescindibleNuncaEsPrescindible(EvaluadorCriticidad evaluador) {
        // El flag lo puso el grupo a mano. Ignorarlo sería contradecir una
        // decisión explícita de las personas que usan el producto.
        var v = evaluador.evaluar(planCreadoPor(ANA), miembro(true, MiembroGrupo.Rol.MIEMBRO), BRUNO);

        assertThat(v.esCritica()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("implementaciones")
    @DisplayName("Contrato 6: un plan sin creador registrado no revienta")
    void elPlanSinCreadorNoRevienta(EvaluadorCriticidad evaluador) {
        Plan huerfano = Plan.builder().id(UUID.randomUUID()).titulo("Cena")
                .grupo(Grupo.builder().id(UUID.randomUUID()).nombre("G").build())
                .build();

        assertThatCode(() -> evaluador.evaluar(huerfano, miembro(false, MiembroGrupo.Rol.MIEMBRO), ANA))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("implementaciones")
    @DisplayName("Contrato 7: es determinista — dos llamadas iguales dan lo mismo")
    void esDeterminista(EvaluadorCriticidad evaluador) {
        // Un evaluador con modelo tiene que fijar la temperatura o cachear:
        // que la misma ausencia abra votación o no según el momento haría el
        // producto imposible de explicar.
        Plan plan = planCreadoPor(ANA);
        MiembroGrupo m = miembro(false, MiembroGrupo.Rol.MIEMBRO);

        var primera = evaluador.evaluar(plan, m, BRUNO);
        var segunda = evaluador.evaluar(plan, m, BRUNO);

        assertThat(segunda.criticidad()).isEqualTo(primera.criticidad());
    }

    /* ------------------------------------------------------------------ */

    /** Los casos que cubren el espacio: creador, imprescindible, rol y combinaciones. */
    private static List<Object[]> casos() {
        Plan plan = planCreadoPor(ANA);
        return List.of(
                new Object[]{plan, miembro(false, MiembroGrupo.Rol.MIEMBRO), ANA, "creador"},
                new Object[]{plan, miembro(true, MiembroGrupo.Rol.MIEMBRO), BRUNO, "imprescindible"},
                new Object[]{plan, miembro(false, MiembroGrupo.Rol.ORGANIZADOR), BRUNO, "organizador"},
                new Object[]{plan, miembro(true, MiembroGrupo.Rol.ORGANIZADOR), ANA, "todo a la vez"},
                new Object[]{plan, miembro(false, MiembroGrupo.Rol.MIEMBRO), BRUNO, "miembro raso"}
        );
    }

    private static Plan planCreadoPor(UUID usuarioId) {
        return Plan.builder()
                .id(UUID.randomUUID())
                .titulo("Cena")
                .grupo(Grupo.builder().id(UUID.randomUUID()).nombre("G").build())
                .creadoPor(Usuario.builder().id(usuarioId).nombre("Quien sea").build())
                .build();
    }

    private static MiembroGrupo miembro(boolean imprescindible, MiembroGrupo.Rol rol) {
        return MiembroGrupo.builder().rol(rol).esImprescindible(imprescindible).build();
    }
}
