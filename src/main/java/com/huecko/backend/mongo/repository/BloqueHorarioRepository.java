package com.huecko.backend.mongo.repository;

import com.huecko.backend.mongo.document.BloqueHorario;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BloqueHorarioRepository extends MongoRepository<BloqueHorario, String> {

    List<BloqueHorario> findByUsuarioId(String usuarioId);

    List<BloqueHorario> findByUsuarioIdIn(List<String> usuarioIds);

    List<BloqueHorario> findByUsuarioIdAndEstado(String usuarioId, BloqueHorario.Estado estado);

    /**
     * Base del cruce del Módulo 2. Filtra por estado en la propia consulta para
     * que los borradores de OCR nunca entren en el cálculo (RNF-06), y para no
     * traerse a memoria bloques que se iban a descartar igual.
     */
    List<BloqueHorario> findByUsuarioIdInAndEstado(List<String> usuarioIds, BloqueHorario.Estado estado);
}
