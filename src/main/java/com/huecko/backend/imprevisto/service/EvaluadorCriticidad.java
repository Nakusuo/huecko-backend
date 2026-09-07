package com.huecko.backend.imprevisto.service;

import com.huecko.backend.mongo.document.VotacionExpres;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;

import java.util.UUID;

/**
 * RF-16: decide si una ausencia es crítica.
 *
 * Es una interfaz y no una clase porque el documento lo anticipa: el MVP usa
 * reglas —«sistema de reglas, costo cero»— y deja **un LLM como mejora de fase
 * 2**. Cuando llegue esa fase, se añade otra implementación y se cambia una
 * propiedad; nada del Módulo 5 tiene que enterarse.
 *
 * <h2>Contrato que cualquier implementación debe cumplir</h2>
 *
 * <ol>
 *   <li><b>Siempre devuelve un veredicto.</b> Nunca nulo, nunca una excepción
 *       que suba. Una ausencia sin clasificar bloquearía a alguien que solo
 *       intentaba avisar de que no puede ir.</li>
 *   <li><b>Siempre explica por qué.</b> «Tu ausencia abrió una votación» sin
 *       motivo se lee como un castigo arbitrario. La razón se muestra en la
 *       cabecera del panel, así que va en el idioma del usuario y en segunda o
 *       tercera persona, no en jerga de sistema.</li>
 *   <li><b>Dice de dónde salió.</b> El {@link Origen} viaja hasta la interfaz:
 *       un grupo tiene derecho a saber si la decisión la tomó una regla o un
 *       modelo.</li>
 *   <li><b>Es rápida y no bloquea.</b> Corre dentro de la petición de reportar
 *       el imprevisto. Una implementación que llame por red tiene que poner
 *       tiempo máximo y caer a reglas si se pasa — ver {@link Origen#REGLAS_POR_FALLO}.</li>
 *   <li><b>No inventa datos.</b> Solo puede usar lo que recibe. No consulta el
 *       histórico del grupo ni el contenido de otros planes.</li>
 * </ol>
 *
 * <p>Las pruebas de contrato viven en {@code EvaluadorCriticidadContractTest} y
 * se ejecutan contra <b>toda</b> implementación registrada. Una nueva no entra
 * sin pasarlas.
 */
public interface EvaluadorCriticidad {

    /** De dónde salió el veredicto. Viaja hasta la interfaz. */
    enum Origen {
        /** Las reglas del MVP. */
        REGLAS,
        /** Un modelo. Fase 2. */
        IA,
        /**
         * Se intentó con modelo y no se pudo —tiempo agotado, error, respuesta
         * ilegible— así que respondieron las reglas. **No es lo mismo que
         * REGLAS**: aquí hubo una degradación que conviene poder medir.
         */
        REGLAS_POR_FALLO
    }

    record Veredicto(VotacionExpres.Criticidad criticidad, String razon, Origen origen) {

        public boolean esCritica() {
            return criticidad == VotacionExpres.Criticidad.CRITICA;
        }

        public static Veredicto critica(String razon, Origen origen) {
            return new Veredicto(VotacionExpres.Criticidad.CRITICA, razon, origen);
        }

        public static Veredicto noCritica(String razon, Origen origen) {
            return new Veredicto(VotacionExpres.Criticidad.NO_CRITICA, razon, origen);
        }
    }

    /**
     * @param plan      el plan afectado, con su creador
     * @param miembro   la membresía de quien reporta, con su rol y su flag
     * @param usuarioId quien reporta
     */
    Veredicto evaluar(Plan plan, MiembroGrupo miembro, UUID usuarioId);
}
