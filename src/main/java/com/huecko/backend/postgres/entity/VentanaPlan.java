package com.huecko.backend.postgres.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Una de las 2 a 5 opciones de fecha y hora que se someten a votación (RF-08).
 *
 * Guarda una FECHA concreta, no un día de la semana. El heatmap razona en
 * "martes de 10 a 12" porque describe una rutina; un plan, en cambio, ocurre
 * un día concreto, y sin la fecha no habría forma de convertir la ventana
 * ganadora en un evento con fecha y hora reales (RF-11).
 */
@Entity
@Table(name = "ventanas_plan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentanaPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    /**
     * Porcentaje del grupo libre en esta franja, medido en el momento de
     * proponer el plan (RF-08).
     *
     * Es una foto, no un dato vivo: si alguien añade una clase después, este
     * número no se actualiza. Se guarda igualmente porque es lo que el grupo
     * vio al votar, y recalcularlo al vuelo cambiaría el histórico de una
     * votación ya cerrada.
     */
    @Column(name = "disponibilidad_porcentaje", nullable = false)
    private int disponibilidadPorcentaje;

    /** 1 = lunes … 7 = domingo. Derivado de la fecha, para no recalcularlo al pintar. */
    public int diaSemana() {
        return fecha.getDayOfWeek().getValue();
    }
}
