package com.huecko.backend.postgres.repository;

import com.huecko.backend.postgres.entity.VotoVentana;
import com.huecko.backend.postgres.entity.VotoVentanaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface VotoVentanaRepository extends JpaRepository<VotoVentana, VotoVentanaId> {

    /**
     * Todos los votos de un plan de una vez. Se resuelve el recuento por ventana
     * en memoria en vez de con una consulta por ventana: son 5 ventanas como
     * mucho (RF-08) y asi el cierre de la votacion hace una sola lectura.
     */
    @Query("select v from VotoVentana v join fetch v.usuario where v.ventana.plan.id = :planId")
    List<VotoVentana> findByPlanId(UUID planId);

    @Query("select v from VotoVentana v where v.ventana.plan.id = :planId and v.usuario.id = :usuarioId")
    List<VotoVentana> findByPlanIdAndUsuarioId(UUID planId, UUID usuarioId);
}
