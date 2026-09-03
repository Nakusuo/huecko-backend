package com.huecko.backend.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
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
    @Indexed
    private String usuarioId;

    private Tipo tipo;

    // Solo aplica si tipo = RECURRENTE (1 = lunes ... 7 = domingo)
    private Integer diaSemana;

    // Solo aplica si tipo = PUNTUAL
    private LocalDate fecha;

    private LocalTime horaInicio;
    private LocalTime horaFin;

    private String etiqueta;

    private Fuente fuente;

    @Builder.Default
    private Estado estado = Estado.CONFIRMADO;

    private Instant creadoEn;
    private Instant actualizadoEn;
}
