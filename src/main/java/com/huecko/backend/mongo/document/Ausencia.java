package com.huecko.backend.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Colección: ausencias (RF-15, RF-19).
 *
 * Antes solo se guardaba la ausencia crítica, dentro de su votación exprés; la
 * no crítica era un aviso por WebSocket y nada más. Con eso, al recargar nadie
 * sabía quién no venía, y como no quedaba rastro se podía repetir el aviso sin
 * límite, notificando al grupo entero cada vez.
 *
 * Se guardan las dos, críticas y no críticas, porque para el grupo son el mismo
 * hecho («X no viene»); la criticidad solo decide si además se vota.
 *
 * <b>Una por persona y plan.</b> El índice único lo garantiza también frente a
 * dos avisos simultáneos, que la comprobación previa del servicio no frena.
 * Mismo criterio que {@link AlertaRetraso}, y misma colección de Mongo por la
 * misma razón: un hecho suelto, sin relaciones que mantener.
 */
@Document(collection = "ausencias")
@CompoundIndex(name = "uniq_plan_usuario", def = "{'planId': 1, 'usuarioId': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ausencia {

    @Id
    private String id;

    /** UUID del plan en Postgres. */
    private String planId;

    private String grupoId;

    /** UUID del usuario en Postgres. */
    private String usuarioId;

    /** Nombre en el momento del aviso, duplicado como en AlertaRetraso: una sola lectura para pintarlas. */
    private String nombreUsuario;

    /** RF-15: opcional. */
    private String motivo;

    /** Si abrió votación exprés (RF-17) o solo se informó (RF-19). */
    private boolean critica;

    private Instant reportadoEn;
}
