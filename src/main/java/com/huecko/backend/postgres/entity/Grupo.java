package com.huecko.backend.postgres.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "grupos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Grupo {

    /** Umbral por defecto: unanimidad (HU-05). El grupo puede bajarlo (HU-06). */
    public static final int UMBRAL_POR_DEFECTO = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 400)
    private String descripcion;

    /**
     * Código con el que alguien se une al grupo (HU-05, RF-08 lo necesita para
     * que el grupo tenga integrantes). Único: es la clave de búsqueda al unirse.
     */
    @Column(name = "codigo_invitacion", nullable = false, unique = true, length = 20)
    private String codigoInvitacion;

    /**
     * RF-06: porcentaje mínimo de integrantes libres para que una franja cuente
     * como hueco del grupo. 100 = unanimidad.
     */
    @Column(name = "umbral_disponibilidad", nullable = false)
    @Builder.Default
    private int umbralDisponibilidad = UMBRAL_POR_DEFECTO;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por", nullable = false)
    private Usuario creadoPor;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @PrePersist
    void prePersist() {
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
    }
}
