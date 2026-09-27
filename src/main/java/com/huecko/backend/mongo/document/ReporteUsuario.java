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
 * Lo que una persona manda desde «Reportar un problema». Nombres y correos van
 * copiados, como en AlertaRetraso: el reporte se entiende aunque la cuenta
 * cambie de nombre después.
 */
@Document(collection = "reportes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteUsuario {

    /** FALLO = algo de la app no funciona; CONDUCTA = problema con otra cuenta. */
    public enum Tipo { FALLO, CONDUCTA }

    @Id
    private String id;

    private Tipo tipo;

    private String descripcion;

    /** Página en la que estaba al reportar. */
    private String ruta;

    private String navegador;

    @Indexed
    private String autorId;

    private String autorNombre;

    private String autorEmail;

    /** Solo en CONDUCTA: la cuenta de la que se informa. */
    private String cuentaReportadaId;

    private String cuentaReportadaNombre;

    private String cuentaReportadaEmail;

    private EstadoRevision estado;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
