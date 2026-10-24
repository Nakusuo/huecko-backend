package com.huecko.backend.postgres.repository;

import com.huecko.backend.postgres.entity.ComentarioFoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ComentarioFotoRepository extends JpaRepository<ComentarioFoto, UUID> {

    List<ComentarioFoto> findByFotoIdOrderByCreadoEnAsc(UUID fotoId);

    /** Cuántos comentarios tiene cada foto: filas `[fotoId, total]`. */
    @Query("select c.fotoId, count(c) from ComentarioFoto c where c.fotoId in :fotoIds group by c.fotoId")
    List<Object[]> contarPorFoto(Collection<UUID> fotoIds);
}
