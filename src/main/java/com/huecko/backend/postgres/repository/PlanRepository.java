package com.huecko.backend.postgres.repository;

import com.huecko.backend.postgres.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    /**
     * Planes de un grupo, del mas reciente al mas antiguo. `join fetch` de las
     * ventanas porque la respuesta las incluye siempre: sin el serian N+1
     * consultas para pintar una lista.
     */
    @Query("select distinct p from Plan p left join fetch p.ventanas "
            + "where p.grupo.id = :grupoId order by p.creadoEn desc")
    List<Plan> findByGrupoConVentanas(UUID grupoId);

    @Query("select p from Plan p left join fetch p.ventanas where p.id = :planId")
    Optional<Plan> findByIdConVentanas(UUID planId);

    /** RF-10: los que ya vencieron y siguen abiertos. Es lo que barre el cierre automatico. */
    List<Plan> findByEstadoAndPlazoVotacionLessThanEqual(Plan.Estado estado, Instant limite);
}
