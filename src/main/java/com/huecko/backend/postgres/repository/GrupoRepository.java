package com.huecko.backend.postgres.repository;

import com.huecko.backend.postgres.entity.Grupo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GrupoRepository extends JpaRepository<Grupo, UUID> {

    Optional<Grupo> findByNombreIgnoreCase(String nombre);

    /** Grupos a los que pertenece un usuario, sea como organizador o como miembro. */
    @Query("select m.grupo from MiembroGrupo m where m.usuario.id = :usuarioId order by m.grupo.creadoEn")
    List<Grupo> findByMiembro(UUID usuarioId);
}
