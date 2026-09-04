package com.huecko.backend.postgres.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "miembros_grupo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(MiembroGrupoId.class)
public class MiembroGrupo {

    public enum Rol { ORGANIZADOR, MIEMBRO }

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_id")
    private Grupo grupo;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    // Usado por el módulo de IA ante imprevistos (RF-16) para evaluar criticidad
    @Column(name = "es_imprescindible", nullable = false)
    private boolean esImprescindible;
}
