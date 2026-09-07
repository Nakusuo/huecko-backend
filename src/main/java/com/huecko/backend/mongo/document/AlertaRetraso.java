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
 * Colección: alertas_retraso (RF-12, RF-14).
 *
 * Va en Mongo y no en Postgres porque no tiene relaciones que mantener: es un
 * hecho suelto que se escribe una vez y se lee para pintar la fila de
 * puntualidad del evento.
 *
 * <b>Una alerta por persona y plan.</b> El índice único lo garantiza: quien
 * dijo 10 minutos y luego se da cuenta de que serán 25 actualiza la suya, no
 * crea una segunda. Sin eso, la vista del evento mostraría a la misma persona
 * dos veces con retrasos distintos y nadie sabría cuál vale.
 */
@Document(collection = "alertas_retraso")
@CompoundIndex(name = "idx_plan_usuario", def = "{'planId': 1, 'usuarioId': 1}", unique = true)
@CompoundIndex(name = "idx_plan", def = "{'planId': 1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertaRetraso {

    @Id
    private String id;

    /** UUID del plan en Postgres. */
    private String planId;

    /** Se guarda para poder publicar al topic sin volver a Postgres. */
    private String grupoId;

    /** UUID del usuario en Postgres. */
    private String usuarioId;

    /**
     * Nombre en el momento del aviso. Duplicado a propósito: la fila de
     * puntualidad se pinta con una sola lectura a Mongo, sin ir a buscar a
     * Postgres el nombre de cada rezagado.
     */
    private String nombreUsuario;

    private int minutosEstimados;

    private Instant creadoEn;

    /** Se actualiza si la persona corrige su estimación. */
    private Instant actualizadoEn;
}
