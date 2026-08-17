package com.huecko.backend.mongo.repository;

import com.huecko.backend.mongo.document.BloqueHorario;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BloqueHorarioRepository extends MongoRepository<BloqueHorario, String> {

    List<BloqueHorario> findByUsuarioId(String usuarioId);

    List<BloqueHorario> findByUsuarioIdIn(List<String> usuarioIds);

    List<BloqueHorario> findByUsuarioIdAndEstado(String usuarioId, BloqueHorario.Estado estado);
}
