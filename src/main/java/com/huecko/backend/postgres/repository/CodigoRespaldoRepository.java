package com.huecko.backend.postgres.repository;

import com.huecko.backend.postgres.entity.CodigoRespaldo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface CodigoRespaldoRepository extends JpaRepository<CodigoRespaldo, UUID> {

    Optional<CodigoRespaldo> findByUsuarioIdAndCodigoHashAndUsadoEnIsNull(UUID usuarioId, String codigoHash);

    long countByUsuarioIdAndUsadoEnIsNull(UUID usuarioId);

    /** Al generar otros o desactivar la verificación, los anteriores dejan de valer. */
    @Modifying
    @Query("delete from CodigoRespaldo c where c.usuarioId = :usuarioId")
    int borrarDelUsuario(UUID usuarioId);
}
