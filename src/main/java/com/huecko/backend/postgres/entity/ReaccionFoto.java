package com.huecko.backend.postgres.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Una reacción de una persona a una foto del álbum. */
@Entity
@Table(name = "reacciones_foto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReaccionFoto {

    /** Las que se ofrecen; en la interfaz son ❤️ 😂 🔥 😮 👏. */
    public enum Reaccion { CORAZON, RISA, FUEGO, ASOMBRO, APLAUSO }

    @EmbeddedId
    private Clave id;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Clave implements Serializable {

        @Column(name = "foto_id", nullable = false)
        private UUID fotoId;

        @Column(name = "usuario_id", nullable = false)
        private UUID usuarioId;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 12)
        private Reaccion reaccion;
    }
}
