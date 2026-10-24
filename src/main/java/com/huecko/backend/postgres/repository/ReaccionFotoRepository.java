package com.huecko.backend.postgres.repository;

import com.huecko.backend.postgres.entity.ReaccionFoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReaccionFotoRepository extends JpaRepository<ReaccionFoto, ReaccionFoto.Clave> {

    /** Todas las de varias fotos de una vez: el álbum de un plan las cuenta por foto. */
    @Query("select r from ReaccionFoto r where r.id.fotoId in :fotoIds")
    List<ReaccionFoto> deLasFotos(Collection<UUID> fotoIds);
}
