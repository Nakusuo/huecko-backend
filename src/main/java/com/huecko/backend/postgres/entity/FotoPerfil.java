package com.huecko.backend.postgres.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Foto de perfil: un JPEG de 256 px rehecho por el servidor (ver
 * Imagenes). Va aparte de Usuario para no leer sus bytes en cada consulta.
 */
@Entity
@Table(name = "fotos_perfil")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FotoPerfil {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(nullable = false)
    private byte[] contenido;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;
}
