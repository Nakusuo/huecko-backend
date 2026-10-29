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

/** Código de respaldo de la verificación en dos pasos: de un solo uso, guardado como SHA-256. */
@Entity
@Table(name = "codigos_respaldo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodigoRespaldo {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "codigo_hash", nullable = false, length = 64)
    private String codigoHash;

    /** Nulo mientras no se haya usado. */
    @Column(name = "usado_en")
    private Instant usadoEn;
}
