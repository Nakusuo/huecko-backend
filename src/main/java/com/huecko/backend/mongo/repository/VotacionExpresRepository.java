package com.huecko.backend.mongo.repository;

import com.huecko.backend.mongo.document.VotacionExpres;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VotacionExpresRepository extends MongoRepository<VotacionExpres, String> {

    Optional<VotacionExpres> findByPlanIdAndEstado(String planId, VotacionExpres.Estado estado);

    List<VotacionExpres> findByPlanIdOrderByAbiertaEnDesc(String planId);

    /**
     * RF-18: las que hay que cerrar. El planificador las busca por plazo, no
     * el índice TTL — ver el comentario de {@link VotacionExpres}.
     */
    List<VotacionExpres> findByEstadoAndExpiraEnLessThanEqual(
            VotacionExpres.Estado estado, Instant limite);
}
