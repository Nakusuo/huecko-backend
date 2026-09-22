package com.huecko.backend.postgres.repository;

import com.huecko.backend.postgres.entity.Grupo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GrupoRepository extends JpaRepository<Grupo, UUID> {

    /** Lo usa el seeder de demo. Los nombres no son únicos, por eso también filtra por creador. */
    Optional<Grupo> findFirstByNombreIgnoreCaseAndCreadoPor_Id(String nombre, UUID creadoPorId);

    /**
     * Bloquea la fila del grupo hasta que termine la transacción.
     *
     * Serializa los cambios de membresía de un mismo grupo. Sin él, dos
     * organizadores que se degradaban o se iban a la vez veían cada uno "hay
     * otro organizador" y el grupo quedaba sin ninguno.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Grupo g where g.id = :grupoId")
    Optional<Grupo> bloquearPorId(UUID grupoId);

    /** Si el grupo tiene planes, borrarlo rompería la clave foránea de `planes`. */
    @Query("select case when count(p) > 0 then true else false end from Plan p where p.grupo.id = :grupoId")
    boolean tienePlanes(UUID grupoId);

    /**
     * Grupos a los que pertenece un usuario, sea como organizador o como miembro.
     * `join fetch` sobre creadoPor porque la respuesta incluye quién creó el grupo
     * y sin él Hibernate lanzaría una consulta extra por cada grupo de la lista.
     */
    @Query("select m.grupo from MiembroGrupo m join fetch m.grupo.creadoPor "
            + "where m.usuario.id = :usuarioId order by m.grupo.creadoEn")
    List<Grupo> findByMiembro(UUID usuarioId);
}
