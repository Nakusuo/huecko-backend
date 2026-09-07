package com.huecko.backend.postgres.repository;

import com.huecko.backend.postgres.entity.Grupo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GrupoRepository extends JpaRepository<Grupo, UUID> {

    Optional<Grupo> findByNombreIgnoreCase(String nombre);



    /**
     * Grupos a los que pertenece un usuario, sea como organizador o como miembro.
     * `join fetch` sobre creadoPor porque la respuesta incluye quién creó el grupo
     * y sin él Hibernate lanzaría una consulta extra por cada grupo de la lista.
     */
    @Query("select m.grupo from MiembroGrupo m join fetch m.grupo.creadoPor "
            + "where m.usuario.id = :usuarioId order by m.grupo.creadoEn")
    List<Grupo> findByMiembro(UUID usuarioId);
}
