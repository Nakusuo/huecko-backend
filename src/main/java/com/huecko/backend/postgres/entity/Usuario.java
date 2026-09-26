package com.huecko.backend.postgres.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    /**
     * Rol en la plataforma. No confundir con MiembroGrupo.Rol, que es el papel
     * de alguien dentro de un grupo: un ADMIN opera la plataforma y no forma
     * parte de ningún grupo.
     */
    public enum RolSistema { USUARIO, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /* El DEFAULT de la columna es para las filas que ya existían: sin él,
       `ddl-auto: update` no puede añadir una columna NOT NULL a una tabla con datos. */
    @Enumerated(EnumType.STRING)
    @Column(name = "rol_sistema", nullable = false, length = 20)
    @ColumnDefault("'USUARIO'")
    @Builder.Default
    private RolSistema rolSistema = RolSistema.USUARIO;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @PrePersist
    void prePersist() {
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
    }
}
