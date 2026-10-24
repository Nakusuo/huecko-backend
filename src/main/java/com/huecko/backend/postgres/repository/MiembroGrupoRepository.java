package com.huecko.backend.postgres.repository;

import com.huecko.backend.postgres.entity.Grupo;
import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.MiembroGrupoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MiembroGrupoRepository extends JpaRepository<MiembroGrupo, MiembroGrupoId> {

    @Query("select m.usuario.id from MiembroGrupo m where m.grupo.id = :grupoId")
    List<UUID> findUsuarioIdsByGrupoId(UUID grupoId);

    /**
     * Integrantes de un grupo con su usuario ya cargado: la respuesta necesita
     * nombre y email de cada uno, y sin el `join fetch` serían N consultas.
     */
    @Query("select m from MiembroGrupo m join fetch m.usuario "
            + "where m.grupo.id = :grupoId order by m.rol, m.usuario.nombre")
    List<MiembroGrupo> findByGrupoIdConUsuario(UUID grupoId);

    Optional<MiembroGrupo> findByGrupo_IdAndUsuario_Id(UUID grupoId, UUID usuarioId);

    boolean existsByGrupo_IdAndUsuario_Id(UUID grupoId, UUID usuarioId);

    long countByGrupo_Id(UUID grupoId);

    /**
     * Grupos en los que están las dos personas. Es lo que da permiso para ver
     * la foto y el perfil de alguien: sin un grupo en común, esa cuenta no
     * existe para ti.
     */
    @Query("select a.grupo from MiembroGrupo a, MiembroGrupo b "
            + "where a.grupo = b.grupo and a.usuario.id = :una and b.usuario.id = :otra "
            + "order by a.grupo.nombre")
    List<Grupo> gruposEnComun(UUID una, UUID otra);
}
