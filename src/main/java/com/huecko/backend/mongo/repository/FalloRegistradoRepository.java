package com.huecko.backend.mongo.repository;

import com.huecko.backend.mongo.document.EstadoRevision;
import com.huecko.backend.mongo.document.FalloRegistrado;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FalloRegistradoRepository extends MongoRepository<FalloRegistrado, String> {

    long countByEstado(EstadoRevision estado);
}
