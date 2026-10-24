package com.huecko.backend.postgres.repository;

import com.huecko.backend.postgres.entity.Llegada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LlegadaRepository extends JpaRepository<Llegada, Llegada.Clave> {

    @Query("select l from Llegada l where l.id.planId = :planId order by l.llegoEn")
    List<Llegada> delPlan(UUID planId);

    /** Las de varios planes de una vez, para los logros del perfil. */
    @Query("select l from Llegada l where l.id.planId in :planIds")
    List<Llegada> deLosPlanes(Collection<UUID> planIds);

    /** Al reagendar, las llegadas hablan de una fecha que ya no existe. */
    @Modifying
    @Query("delete from Llegada l where l.id.planId = :planId")
    int borrarDelPlan(UUID planId);
}
