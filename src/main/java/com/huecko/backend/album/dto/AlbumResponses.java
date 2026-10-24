package com.huecko.backend.album.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Respuestas del álbum de recuerdos. Las fotos se piden aparte, por su id. */
public final class AlbumResponses {

    private AlbumResponses() {
    }

    /**
     * Los recuerdos de un grupo: los planes que ya se vivieron, con o sin
     * fotos todavía, y cuánto del tope del grupo se ha usado.
     */
    public record AlbumGrupo(int usadas, int maximo, List<PlanConFotos> planes) {
    }

    /** `portadaId` es la primera foto que se subió; nula si aún no hay ninguna. */
    public record PlanConFotos(UUID planId, String titulo, LocalDate fecha, LocalTime horaInicio, int fotos,
                               UUID portadaId) {
    }

    /**
     * El álbum de un plan visto por una persona: `puedeSubir` y lo que le
     * queda ya tienen en cuenta si el plan empezó y los dos topes.
     */
    public record AlbumPlan(UUID planId, UUID grupoId, String titulo, LocalDate fecha, LocalTime horaInicio,
                            boolean puedeSubir, int restantesMias, int restantesGrupo, List<Foto> fotos) {
    }

    /**
     * `reacciones` cuenta cada una por su nombre (CORAZON, RISA…); en
     * `misReacciones` van las que dejaste tú.
     */
    public record Foto(UUID id, UUID autorId, String autorNombre, int ancho, int alto, Instant subidaEn,
                       boolean puedeBorrar, Map<String, Integer> reacciones, List<String> misReacciones,
                       int comentarios) {
    }

    /** Reacciones y número de comentarios de una foto, tras reaccionar o comentar. */
    public record Interacciones(UUID fotoId, Map<String, Integer> reacciones, List<String> misReacciones,
                                int comentarios) {

        public static Interacciones vacias(UUID fotoId) {
            return new Interacciones(fotoId, Map.of(), List.of(), 0);
        }
    }

    public record Comentario(UUID id, UUID autorId, String autorNombre, Instant autorFotoActualizadaEn,
                             String texto, Instant creadoEn, boolean puedeBorrar) {
    }
}
