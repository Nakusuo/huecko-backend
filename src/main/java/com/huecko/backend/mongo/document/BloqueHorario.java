package com.huecko.backend.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Colección: bloques_horario
 * Esquema flexible a propósito (RF-01, RF-02, RF-03, RF-04): un bloque
 * recurrente usa diaSemana, uno puntual usa fecha, y uno de origen OCR
 * nace en estado BORRADOR hasta que el usuario lo confirma (RF-03).
 */
@Document(collection = "bloques_horario")
@CompoundIndex(name = "idx_usuario_estado", def = "{'usuarioId': 1, 'estado': 1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BloqueHorario {

    public enum Tipo { RECURRENTE, PUNTUAL }
    public enum Fuente { MANUAL, OCR }
    public enum Estado { BORRADOR, CONFIRMADO }

    @Id
    private String id;

    // UUID del usuario en Postgres — el "join" entre ambas bases se hace por este campo
    @Indexed(name = "idx_usuario")
    private String usuarioId;

    private Tipo tipo;

    // Solo aplica si tipo = RECURRENTE (1 = lunes ... 7 = domingo)
    private Integer diaSemana;

    // Solo aplica si tipo = PUNTUAL
    private LocalDate fecha;

    /**
     * Último día de un bloque puntual que se repite varios días seguidos.
     * Nulo = el bloque dura solo `fecha`.
     */
    private LocalDate fechaFin;

    private LocalTime horaInicio;
    private LocalTime horaFin;

    /** Título que escribe el usuario ("Cálculo II", "Turno en la tienda"). */
    private String etiqueta;

    /** Categoría del bloque: Clase, Trabajo, Personal… Es distinta del título. */
    private String categoria;

    /** Color con el que se pinta el bloque en la rejilla (formato CSS, p. ej. "#7C3AED"). */
    private String color;

    private Fuente fuente;

    @Builder.Default
    private Estado estado = Estado.CONFIRMADO;

    private Instant creadoEn;
    private Instant actualizadoEn;
}
