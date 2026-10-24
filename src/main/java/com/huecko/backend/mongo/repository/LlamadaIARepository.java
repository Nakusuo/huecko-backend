package com.huecko.backend.mongo.repository;

import com.huecko.backend.mongo.document.LlamadaIA;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface LlamadaIARepository extends MongoRepository<LlamadaIA, String> {

    List<LlamadaIA> findByOcurridaEnAfter(Instant desde);
}
