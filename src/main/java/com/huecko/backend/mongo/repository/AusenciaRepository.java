package com.huecko.backend.mongo.repository;

import com.huecko.backend.mongo.document.Ausencia;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AusenciaRepository extends MongoRepository<Ausencia, String> {

    /** Quién no viene, en el orden en que avisaron. */
    List<Ausencia> findByPlanIdOrderByReportadoEnAsc(String planId);

    boolean existsByPlanIdAndUsuarioId(String planId, String usuarioId);

    /** Para deshacer el alta si la votación exprés que debía acompañarla no llega a abrirse. */
    long deleteByPlanIdAndUsuarioId(String planId, String usuarioId);

    /** Al reagendar: las ausencias eran para la fecha anterior. */
    long deleteByPlanId(String planId);
}
