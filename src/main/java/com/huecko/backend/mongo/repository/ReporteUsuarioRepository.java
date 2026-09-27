package com.huecko.backend.mongo.repository;

import com.huecko.backend.mongo.document.EstadoRevision;
import com.huecko.backend.mongo.document.ReporteUsuario;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;

public interface ReporteUsuarioRepository extends MongoRepository<ReporteUsuario, String> {

    /** Para el límite de reportes por hora de cada persona. */
    long countByAutorIdAndCreadoEnAfter(String autorId, Instant desde);

    long countByEstado(EstadoRevision estado);
}
