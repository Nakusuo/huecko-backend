package com.huecko.backend.imprevisto.service;

import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * RF-16 con reglas. La implementación del MVP.
 *
 * El documento lo deja explícito: «sistema de reglas (rol del usuario + flag
 * imprescindible), costo cero. Un LLM queda como mejora de fase 2». Encaja con
 * RNF-09, que obliga a capas gratuitas.
 *
 * Tres motivos hacen crítica una ausencia, <b>en este orden</b>:
 *
 * <ol>
 *   <li>Quien falta <b>propuso el plan</b>. Si el organizador no va, muchas
 *       veces no hay plan.</li>
 *   <li>El grupo lo marcó como <b>imprescindible</b>. Es el caso del que trae
 *       el coche o tiene la llave del local.</li>
 *   <li>Es <b>organizador del grupo</b>.</li>
 * </ol>
 *
 * El orden importa: la razón que se devuelve es la del primer motivo que se
 * cumple, y «propuso este plan» explica mejor que «es organizador del grupo»
 * cuando ambas son ciertas.
 *
 * <p>Es el bean por defecto. Se apaga con
 * {@code huecko.imprevistos.evaluador=ia} cuando exista la otra implementación.
 */
@Component
@ConditionalOnProperty(name = "huecko.imprevistos.evaluador",
                       havingValue = "reglas", matchIfMissing = true)
public class EvaluadorPorReglas implements EvaluadorCriticidad {

    @Override
    public Veredicto evaluar(Plan plan, MiembroGrupo miembro, UUID usuarioId) {
        if (plan.getCreadoPor() != null && usuarioId.equals(plan.getCreadoPor().getId())) {
            return Veredicto.critica("propuso este plan", Origen.REGLAS);
        }

        if (miembro.isEsImprescindible()) {
            return Veredicto.critica("el grupo lo marcó como imprescindible", Origen.REGLAS);
        }

        if (miembro.getRol() == MiembroGrupo.Rol.ORGANIZADOR) {
            return Veredicto.critica("es organizador del grupo", Origen.REGLAS);
        }

        return Veredicto.noCritica("no cumple ninguna regla de criticidad", Origen.REGLAS);
    }
}
