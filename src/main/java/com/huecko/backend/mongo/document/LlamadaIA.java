package com.huecko.backend.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Una llamada a Huecko IA de las que pide la persona, para que el panel de
 * administración vea cuánto se usa, cuánto falla y cuánto tarda.
 *
 * A propósito no guarda quién la hizo, ni la foto, la frase o la respuesta:
 * para eso basta con contar, y lo que la gente escribe o fotografía no tiene
 * por qué quedarse en ningún sitio.
 */
@Document(collection = "llamadas_ia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlamadaIA {

    public enum Funcion { ESCANEO_HORARIO, BORRADOR_PLAN, IDEAS_PLAN, RESUMEN_VOTACION }

    @Id
    private String id;

    private Funcion funcion;

    /** `false` si la IA no respondió a tiempo o respondió algo inservible. */
    private boolean ok;

    private long duracionMs;

    @Indexed
    private Instant ocurridaEn;

    /** Índice TTL: Mongo borra la llamada cuando llega esta fecha. */
    @Indexed(name = "idx_llamadas_ia_ttl", expireAfterSeconds = 0)
    private Instant purgarEn;
}
