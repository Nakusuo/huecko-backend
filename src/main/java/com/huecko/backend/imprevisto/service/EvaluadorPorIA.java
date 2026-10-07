package com.huecko.backend.imprevisto.service;

import com.huecko.backend.ia.ClienteIA;
import com.huecko.backend.mongo.document.VotacionExpres;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.Plan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * RF-16, fase 2: un modelo lee el motivo de la ausencia.
 *
 * <p>No sustituye a las reglas, las completa:
 *
 * <ol>
 *   <li>Quien <b>propuso el plan</b> o el grupo marcó <b>imprescindible</b>
 *       es crítico siempre (contratos 4 y 5). Lo deciden las reglas y no se
 *       pregunta al modelo: es una decisión explícita del grupo.</li>
 *   <li>Sin motivo escrito, el modelo no tiene nada que leer: también reglas.</li>
 *   <li>El resto —un organizador que solo llegará tarde, un miembro que lleva
 *       las entradas— lo decide el modelo con el motivo. Origen {@code IA}.</li>
 *   <li>Si el servicio falla o tarda, responden las reglas con
 *       {@code REGLAS_POR_FALLO} (contrato 4 de la interfaz).</li>
 * </ol>
 *
 * <p>Se activa con {@code huecko.imprevistos.evaluador=ia}.
 */
@Component
@ConditionalOnProperty(name = "huecko.imprevistos.evaluador", havingValue = "ia")
public class EvaluadorPorIA implements EvaluadorCriticidad {

    private static final Logger log = LoggerFactory.getLogger(EvaluadorPorIA.class);

    private final ClienteIA ia;
    private final EvaluadorPorReglas reglas = new EvaluadorPorReglas();

    public EvaluadorPorIA(ClienteIA ia) {
        this.ia = ia;
    }

    @Override
    public Veredicto evaluar(Plan plan, MiembroGrupo miembro, UUID usuarioId, String motivo) {
        Veredicto porReglas = reglas.evaluar(plan, miembro, usuarioId, motivo);

        boolean propusoElPlan = plan.getCreadoPor() != null && usuarioId.equals(plan.getCreadoPor().getId());
        if (propusoElPlan || miembro.isEsImprescindible() || motivo == null || motivo.isBlank()) {
            return porReglas;
        }

        try {
            ClienteIA.RespuestaCriticidad r = ia.criticidad(new ClienteIA.PeticionCriticidad(
                    plan.getTitulo(), plan.getLugar(), miembro.getRol().name(), motivo));
            return new Veredicto(VotacionExpres.Criticidad.valueOf(r.criticidad()), validar(r.razon()), Origen.IA);
        } catch (RuntimeException ex) {
            // Incluye un enum desconocido o una razón vacía: lo mismo que no responder.
            log.warn("Criticidad sin IA para el plan {}: {}", plan.getId(), ex.getMessage());
            return new Veredicto(porReglas.criticidad(), porReglas.razon(), Origen.REGLAS_POR_FALLO);
        }
    }

    private static String validar(String razon) {
        if (razon == null || razon.isBlank() || razon.strip().length() <= 8) {
            throw new IllegalArgumentException("razón inservible");
        }
        return razon.strip();
    }
}
