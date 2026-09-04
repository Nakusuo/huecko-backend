package com.huecko.backend.postgres.repository;

import com.huecko.backend.postgres.entity.MiembroGrupo;
import com.huecko.backend.postgres.entity.MiembroGrupoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MiembroGrupoRepository extends JpaRepository<MiembroGrupo, MiembroGrupoId> {

    @Query("select m.usuario.id from MiembroGrupo m where m.grupo.id = :grupoId")
    List<UUID> findUsuarioIdsByGrupoId(UUID grupoId);

    boolean existsByGrupo_IdAndUsuario_Id(UUID grupoId, UUID usuarioId);
}
