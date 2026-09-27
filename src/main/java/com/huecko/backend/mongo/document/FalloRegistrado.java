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

/**
 * Un fallo que se repite se guarda una sola vez: la `huella` (tipo + dónde
 * ocurrió + primera línea propia de la traza) lo identifica, y cada repetición
 * solo suma una ocurrencia y mueve `ultimaVez`. Mil errores iguales son una
 * fila con 1000, no mil filas.
 */
@Document(collection = "fallos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FalloRegistrado {

    /** SERVIDOR = excepción no controlada del backend; CLIENTE = error del navegador; TAREA = tarea programada. */
    public enum Origen { SERVIDOR, CLIENTE, TAREA }

    @Id
    private String id;

    @Indexed(unique = true)
    private String huella;

    private Origen origen;

    /** Clase de la excepción o nombre del error de JavaScript. */
    private String tipo;

    /** Mensaje de la última vez, recortado. */
    private String mensaje;

    /** Endpoint (`GET /api/planes/{id}`), página del frontend o nombre de la tarea. */
    private String ubicacion;

    /** Traza de la última vez, recortada. */
    private String traza;

    /** Navegador de la última vez (solo CLIENTE). */
    private String navegador;

    private long ocurrencias;

    private Instant primeraVez;

    private Instant ultimaVez;

    private EstadoRevision estado;

    /** Volvió a ocurrir después de darlo por resuelto. */
    private boolean reabierto;
}
