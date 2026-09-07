package com.huecko.backend.postgres.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * El voto de una persona por una ventana (RF-09).
 *
 * La clave primaria es el par (ventana, usuario), igual que en MiembroGrupo:
 * asi la base misma impide que alguien vote dos veces la misma opcion, sin
 * que el servicio tenga que comprobarlo antes de cada insercion.
 */
@Entity
@Table(name = "votos_ventana")
@IdClass(VotoVentanaId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VotoVentana {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ventana_id")
    private VentanaPlan ventana;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @PrePersist
    void prePersist() {
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
    }
}
