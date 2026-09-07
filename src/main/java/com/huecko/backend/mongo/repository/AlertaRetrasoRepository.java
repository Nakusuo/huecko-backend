package com.huecko.backend.mongo.repository;

import com.huecko.backend.mongo.document.AlertaRetraso;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AlertaRetrasoRepository extends MongoRepository<AlertaRetraso, String> {

    /** RF-14: la fila de puntualidad del evento, en una sola lectura. */
    List<AlertaRetraso> findByPlanIdOrderByCreadoEnAsc(String planId);

    /** Para corregir la estimación en vez de acumular avisos. */
    Optional<AlertaRetraso> findByPlanIdAndUsuarioId(String planId, String usuarioId);

    void deleteByPlanIdAndUsuarioId(String planId, String usuarioId);
}
