package com.huecko.backend.postgres.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Una propuesta de plan sometida a votación (HU-08, HU-09, HU-10).
 *
 * Vive en Postgres y no en Mongo, al revés que los bloques de horario: aquí
 * lo que importa son las relaciones (plan → ventanas → votos → usuarios) y la
 * unicidad de "un voto por persona y ventana", que es justo lo que una base
 * relacional sabe garantizar sola.
 */
@Entity
@Table(name = "planes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    /**
     * `EN_RECOORDINACION` es del Módulo 5: un imprevisto crítico reabre un plan
     * ya confirmado. Se declara desde ahora para no tener que migrar el enum
     * cuando llegue ese módulo.
     */
    public enum Estado { PROPUESTO, CONFIRMADO, CANCELADO, EN_RECOORDINACION }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_id", nullable = false)
    private Grupo grupo;

    @Column(nullable = false, length = 120)
    private String titulo;

    /** Opcional (HU-08): un plan puede proponerse sin decidir todavía dónde. */
    @Column(length = 200)
    private String lugar;

    /** Quien propone es el organizador DE ESTE plan, no del grupo entero. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por", nullable = false)
    private Usuario creadoPor;

    /** RF-09: instante en que la votación deja de aceptar votos. */
    @Column(name = "plazo_votacion", nullable = false)
    private Instant plazoVotacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Estado estado = Estado.PROPUESTO;

    /** HU-09: si cada persona puede marcar varias ventanas o solo una. */
    @Column(name = "votos_multiples", nullable = false)
    @Builder.Default
    private boolean votosMultiples = true;

    /**
     * RF-10: la ventana ganadora, una vez cerrada la votación.
     *
     * Es una referencia a una de las `ventanas` de abajo, no una copia de la
     * fecha y la hora: si la ventana cambiara, una copia se desincronizaría.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ventana_confirmada_id")
    private VentanaPlan ventanaConfirmada;

    /**
     * `cascade = ALL` + `orphanRemoval`: las ventanas no existen fuera de su
     * plan, así que borrar el plan tiene que llevárselas por delante.
     */
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fecha, horaInicio")
    @Builder.Default
    private List<VentanaPlan> ventanas = new ArrayList<>();

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    /** Cuándo se cerró la votación, por plazo vencido o a mano. */
    @Column(name = "cerrado_en")
    private Instant cerradoEn;

    @PrePersist
    void prePersist() {
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
    }

    /** La votación sigue viva: ni cerrada ni vencida. */
    public boolean aceptaVotos(Instant ahora) {
        return estado == Estado.PROPUESTO && plazoVotacion.isAfter(ahora);
    }
}
