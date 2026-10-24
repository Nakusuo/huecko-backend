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

/** Un comentario corto en una foto del álbum. */
@Entity
@Table(name = "comentarios_foto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComentarioFoto {

    @Id
    private UUID id;

    @Column(name = "foto_id", nullable = false)
    private UUID fotoId;

    @Column(name = "autor_id", nullable = false)
    private UUID autorId;

    @Column(nullable = false, length = 300)
    private String texto;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;
}
