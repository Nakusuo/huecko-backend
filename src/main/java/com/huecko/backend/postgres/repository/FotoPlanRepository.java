package com.huecko.backend.postgres.repository;

import com.huecko.backend.postgres.entity.FotoPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FotoPlanRepository extends JpaRepository<FotoPlan, UUID> {

    /** Una foto sin sus bytes: lo que hace falta para listar y para comprobar permisos. */
    interface FotoPlanResumen {
        UUID getId();

        UUID getPlanId();

        UUID getGrupoId();

        UUID getAutorId();

        int getAncho();

        int getAlto();

        Instant getSubidaEn();
    }

    List<FotoPlanResumen> findByPlanIdOrderBySubidaEnAsc(UUID planId);

    List<FotoPlanResumen> findByGrupoIdOrderBySubidaEnAsc(UUID grupoId);

    Optional<FotoPlanResumen> findResumenById(UUID id);

    long countByGrupoId(UUID grupoId);

    long countByPlanIdAndAutorId(UUID planId, UUID autorId);

    @Query("select f.contenido from FotoPlan f where f.id = :id")
    Optional<byte[]> contenido(UUID id);

    @Query("select f.miniatura from FotoPlan f where f.id = :id")
    Optional<byte[]> miniatura(UUID id);
}
