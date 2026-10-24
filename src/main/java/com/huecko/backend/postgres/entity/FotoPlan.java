package com.huecko.backend.postgres.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Una foto del álbum de un plan.
 *
 * Los ids van como columnas sueltas, sin relaciones: lo normal es listar
 * fotos (con FotoPlanResumen, que no trae los bytes) o servir una sola, y en
 * ninguno de los dos casos hace falta cargar el plan ni el grupo.
 */
@Entity
@Table(name = "fotos_plan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FotoPlan {

    @Id
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "grupo_id", nullable = false)
    private UUID grupoId;

    @Column(name = "autor_id", nullable = false)
    private UUID autorId;

    @Column(nullable = false)
    private byte[] contenido;

    @Column(nullable = false)
    private byte[] miniatura;

    @Column(nullable = false)
    private int ancho;

    @Column(nullable = false)
    private int alto;

    @Column(name = "subida_en", nullable = false)
    private Instant subidaEn;
}
