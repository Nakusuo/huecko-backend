package com.huecko.backend.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Colección: votaciones_expres (RF-17, RF-18).
 *
 * <b>El TTL no cierra la votación, solo limpia después.</b> Es la trampa de
 * este módulo. El documento de arquitectura propone un índice TTL sobre el
 * plazo «para que el documento expire solo, sin necesitar Redis», pero si
 * Mongo borrara la votación al vencer, RF-18 —aplicar un resultado por defecto
 * cuando no hay quórum— no llegaría a ocurrir nunca: el barrido de TTL corre
 * cada 60 s por su cuenta y se llevaría el documento antes de que nadie
 * decidiera nada.
 *
 * Por eso hay dos campos de tiempo distintos:
 * <ul>
 *   <li>{@code expiraEn} — el plazo de la votación. Lo vigila el planificador,
 *       que cierra y aplica el resultado.</li>
 *   <li>{@code purgarEn} — cuándo puede desaparecer el documento. <b>Solo se
 *       rellena al cerrar.</b> Mongo ignora los documentos donde el campo del
 *       índice TTL está ausente, así que una votación abierta no se borra por
 *       mucho que se retrase el barrido.</li>
 * </ul>
 */
@Document(collection = "votaciones_expres")
@CompoundIndex(name = "idx_estado_expira", def = "{'estado': 1, 'expiraEn': 1}")
/*
 * Como mucho una votación ABIERTA por plan, garantizado por la base. Comprobarlo
 * solo en el servicio no bastaba: dos avisos simultáneos pasaban los dos la
 * comprobación y abrían dos votaciones, y a partir de ahí cada consulta de
 * "la votación abierta" fallaba con un 500.
 */
@CompoundIndex(name = "uniq_abierta_por_plan", def = "{'planId': 1}", unique = true,
               partialFilter = "{ 'estado': 'ABIERTA' }")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VotacionExpres {

    /** RF-15 y RF-17: qué se puede decidir cuando alguien no puede ir. */
    public enum Opcion { CANCELAR, REAGENDAR, MANTENER }

    /** RF-16: resultado de las reglas de criticidad. */
    public enum Criticidad { CRITICA, NO_CRITICA }

    public enum Estado { ABIERTA, CERRADA }

    @Id
    private String id;

    private String planId;

    private String grupoId;

    /** Quién avisa de que no puede ir. */
    private String usuarioReporta;

    private String nombreReporta;

    /** RF-15: el motivo es opcional. */
    private String motivo;

    private Criticidad criticidad;

    /** Por qué se clasificó así, para poder explicárselo al grupo. */
    private String razonCriticidad;

    /**
     * Quién decidió la criticidad: las reglas, un modelo, o las reglas tras
     * fallar el modelo. Se guarda y viaja a la interfaz porque un grupo tiene
     * derecho a saber si la decisión que le abrió una votación la tomó una
     * regla o una IA. Ver EvaluadorCriticidad.Origen.
     */
    private String origenCriticidad;

    private Estado estado;

    /** Voto de cada participante: usuarioId → opción. */
    @Builder.Default
    private Map<String, Opcion> votos = new LinkedHashMap<>();

    /**
     * Integrantes del grupo cuando se abrió, contando a quien reporta. Se
     * guarda el total y no los votantes para no cambiar el significado de los
     * documentos que ya existen; los votantes salen de {@link #votantesPosibles()}.
     */
    private int miembrosDelGrupo;

    private Instant abiertaEn;

    /** RF-17: plazo corto y configurable. Lo vigila el planificador. */
    private Instant expiraEn;

    private Instant cerradaEn;

    /** Opción ganadora, o la de por defecto si no hubo quórum (RF-18). */
    private Opcion resultado;

    /** `true` si el resultado salió de RF-18 y no de los votos. */
    private boolean resultadoPorDefecto;

    /**
     * Índice TTL: Mongo borra el documento cuando llega esta fecha.
     * Nulo mientras la votación está abierta — ver el comentario de la clase.
     */
    @Indexed(name = "idx_purga_ttl", expireAfterSeconds = 0)
    private Instant purgarEn;

    /** Quien reporta no vota: decidir sobre su propia ausencia no le corresponde. */
    public int votantesPosibles() {
        return Math.max(miembrosDelGrupo - 1, 0);
    }

    /**
     * RF-18: votos mínimos para que el resultado cuente. Dos, o todos los que
     * pueden votar si son menos: un único voto en un grupo de seis no es el
     * grupo decidiendo, pero en un grupo de dos es todo el que puede opinar.
     */
    public int quorum() {
        return Math.min(2, votantesPosibles());
    }

    public int votosEmitidos() {
        return votos == null ? 0 : votos.size();
    }
}
