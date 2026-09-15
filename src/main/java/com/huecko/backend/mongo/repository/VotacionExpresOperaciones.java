package com.huecko.backend.mongo.repository;

import com.huecko.backend.mongo.document.VotacionExpres;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

import static org.springframework.data.mongodb.core.FindAndModifyOptions.options;
import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Escrituras atómicas sobre las votaciones exprés.
 *
 * Con `save` del repositorio se reescribe el documento entero a partir de lo
 * que se leyó antes. Eso perdía datos en dos casos reales:
 * <ul>
 *   <li>Dos personas votaban a la vez (el aviso llega a todo el grupo en el
 *       mismo segundo): cada una guardaba su copia y el último voto borraba
 *       al otro, aunque las dos peticiones respondieran 200.</li>
 *   <li>Un voto leído justo antes de vencer se guardaba después de que el
 *       barrido cerrara la votación, y la dejaba otra vez ABIERTA; el
 *       siguiente barrido la cerraba de nuevo, a veces con otro resultado.</li>
 * </ul>
 * Aquí cada operación es una sola orden a Mongo con la condición incluida.
 */
@Component
@RequiredArgsConstructor
public class VotacionExpresOperaciones {

    private final MongoTemplate mongoTemplate;

    /**
     * Registra (o cambia) el voto de una persona, solo si la votación sigue
     * abierta y dentro de plazo. Vacío si no hay ninguna que cumpla eso.
     */
    public Optional<VotacionExpres> registrarVoto(String planId, String usuarioId,
                                                  VotacionExpres.Opcion opcion, Instant ahora) {
        Query query = Query.query(where("planId").is(planId)
                .and("estado").is(VotacionExpres.Estado.ABIERTA)
                .and("expiraEn").gt(ahora));
        Update update = new Update().set("votos." + usuarioId, opcion);

        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, options().returnNew(true), VotacionExpres.class));
    }

    /**
     * Marca la votación como CERRADA si seguía ABIERTA y la devuelve con los
     * votos definitivos. Vacío si otro proceso ya la cerró: así nunca se aplica
     * el resultado dos veces.
     */
    public Optional<VotacionExpres> reclamarParaCerrar(String votacionId, Instant ahora) {
        Query query = Query.query(where("_id").is(votacionId)
                .and("estado").is(VotacionExpres.Estado.ABIERTA));
        Update update = new Update()
                .set("estado", VotacionExpres.Estado.CERRADA)
                .set("cerradaEn", ahora);

        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, options().returnNew(true), VotacionExpres.class));
    }

    /**
     * Deshace un cierre cuyo cambio en el plan no llegó a guardarse en
     * Postgres. La votación vuelve a estar abierta y el próximo barrido lo
     * reintenta, en vez de quedar cerrada con un resultado que no se aplicó.
     */
    public void reabrir(String votacionId) {
        Query query = Query.query(where("_id").is(votacionId)
                .and("estado").is(VotacionExpres.Estado.CERRADA));
        Update update = new Update()
                .set("estado", VotacionExpres.Estado.ABIERTA)
                .set("resultadoPorDefecto", false)
                .unset("resultado")
                .unset("cerradaEn")
                .unset("purgarEn");

        mongoTemplate.updateFirst(query, update, VotacionExpres.class);
    }
}
