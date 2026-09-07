package com.huecko.backend.imprevisto.service;

import com.huecko.backend.mongo.document.VotacionExpres;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * RF-16: decide si una ausencia es crítica.
 *
 * Son reglas, no un modelo. El documento lo deja explícito para el MVP —
 * «sistema de reglas (rol del usuario + flag imprescindible), costo cero. Un
 * LLM queda como mejora de fase 2» — y encaja con RNF-09.
 *
 * Tres motivos hacen crítica una ausencia, en este orden:
 * <ol>
 *   <li>Quien falta <b>propuso el plan</b>. Si el organizador no va, muchas
 *       veces no hay plan.</li>
 *   <li>El grupo lo marcó como <b>imprescindible</b> (el flag que ya existía
 *       en {@code miembros_grupo}). Es el caso del que trae el coche o tiene
 *       la llave del local.</li>
 *   <li>Es <b>organizador del grupo</b>.</li>
 * </ol>
 *
 * Devuelve también la razón, porque «tu ausencia abrió una votación» sin decir
 * por qué se lee como un castigo arbitrario.
 */
@Component
public class EvaluadorCriticidad {

    public record Veredicto(VotacionExpres.Criticidad criticidad, String razon) {

        public boolean esCritica() {
            return criticidad == VotacionExpres.Criticidad.CRITICA;
        }
    }

    public Veredicto evaluar(Plan plan, MiembroGrupo miembro, UUID usuarioId) {
        if (plan.getCreadoPor() != null && usuarioId.equals(plan.getCreadoPor().getId())) {
            return critica("propuso este plan");
        }

        if (miembro.isEsImprescindible()) {
            return critica("el grupo lo marcó como imprescindible");
        }

        if (miembro.getRol() == MiembroGrupo.Rol.ORGANIZADOR) {
            return critica("es organizador del grupo");
        }

        return new Veredicto(VotacionExpres.Criticidad.NO_CRITICA,
                "no cumple ninguna regla de criticidad");
    }

    private Veredicto critica(String razon) {
        return new Veredicto(VotacionExpres.Criticidad.CRITICA, razon);
    }
}
