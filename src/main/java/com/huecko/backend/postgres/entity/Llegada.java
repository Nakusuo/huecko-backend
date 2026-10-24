package com.huecko.backend.postgres.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** «Llegué»: alguien marcó que llegó a un plan confirmado. */
@Entity
@Table(name = "llegadas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Llegada {

    @EmbeddedId
    private Clave id;

    @Column(name = "llego_en", nullable = false)
    private Instant llegoEn;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Clave implements Serializable {

        @Column(name = "plan_id", nullable = false)
        private UUID planId;

        @Column(name = "usuario_id", nullable = false)
        private UUID usuarioId;
    }
}
