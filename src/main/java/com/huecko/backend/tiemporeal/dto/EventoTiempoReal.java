package com.huecko.backend.tiemporeal.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Sobre común de todo lo que sale por WebSocket (RNF-05).
 *
 * El cliente conmuta por `tipo` y lee `datos`. Va un solo sobre para todos los
 * eventos, en vez de un DTO por cada uno, porque el frontend necesita un único
 * punto de entrada al canal: si cada evento tuviera su propia forma, habría que
 * abrir una suscripción por tipo y el orden entre ellas dejaría de estar
 * garantizado.
 *
 * `datos` no lleva nunca la etiqueta de un bloque de horario (RNF-02).
 */
public record EventoTiempoReal(
        Tipo tipo,
        UUID grupoId,
        Instant ocurridoEn,
        Map<String, Object> datos
) {

    /** Cada tipo corresponde a un requerimiento funcional concreto. */
    public enum Tipo {
        /** RF-11: la votación cerró y hay fecha/hora confirmada. */
        PLAN_CONFIRMADO,
        /**
         * Fuera de RF-11. La votación cerró sin ningún voto y el plan quedó
         * cancelado: sin este aviso el grupo seguiría esperando una fecha.
         */
        PLAN_CANCELADO,
        /**
         * RF-08: alguien propuso un plan. Sin esto, el resto del grupo no veía
         * la propuesta hasta recargar, y la votación podía cerrar sin que lo
         * supieran.
         */
        PLAN_PROPUESTO,
        /** Tras un REAGENDAR: el plan vuelve a votarse con ventanas nuevas. */
        PLAN_REAGENDADO,
        /**
         * RF-09: cambió algún voto de ventana de un plan. Solo lleva el plan y
         * quién votó —que ya es público en PlanResponse—, nunca qué ventana:
         * el cliente vuelve a pedir el plan.
         */
        VOTO_ACTUALIZADO,
        /** RF-13: alguien avisa que llegará tarde; el evento no cambia. */
        RETRASO_REPORTADO,
        /** RF-19: baja no crítica, solo se informa. */
        AUSENCIA_REPORTADA,
        /** RF-17: baja crítica, se abre votación exprés. */
        VOTACION_EXPRES_ABIERTA,
        /**
         * RF-17: alguien votó y el recuento cambió.
         *
         * No lleva ni quién votó ni qué votó: el panel enseña el recuento a
         * todo el grupo, pero «mi voto» es de cada uno, así que el cliente
         * vuelve a pedir la votación en vez de reconstruirla del evento. Un
         * topic es de todo el grupo y mandar los votos por él diría a cada
         * miembro lo que votaron los demás (RNF-02).
         */
        VOTO_EXPRES_ACTUALIZADO,
        /** RF-17 y RF-18: la votación exprés terminó, por votos o por defecto. */
        VOTACION_EXPRES_CERRADA,
        /** Alguien entró al grupo con el código de invitación. Lleva su id y su nombre. */
        MIEMBRO_UNIDO,
        /**
         * Alguien avisó de que está libre. Lleva su id, su nombre, hasta cuándo
         * y su nota: lo que escribió esa persona, nunca algo de su horario.
         */
        LIBRE_AHORA,
        /**
         * Cambió el álbum de un plan. Con `subidas` > 0 alguien subió fotos y
         * se avisa; con 0 se borró alguna y solo hay que refrescar.
         */
        ALBUM_ACTUALIZADO,
        /**
         * Cambiaron las reacciones o los comentarios de una foto. Con
         * `comentario` = true hay uno nuevo, y quien subió la foto recibe un
         * aviso (lleva `autorFotoId` para saberlo).
         */
        FOTO_CONVERSACION,
        /** Alguien marcó «Llegué» en un plan. Lleva quién, el plan y si llegó a tiempo. */
        LLEGADA,
        /** El plan confirmado empieza en una hora. */
        RECORDATORIO_PLAN,
        /**
         * La votación del plan cierra en una hora. Va a todo el grupo; el
         * cliente lo enseña solo a quien aún no votó.
         */
        VOTACION_POR_CERRAR
    }

    public static EventoTiempoReal de(Tipo tipo, UUID grupoId, Map<String, Object> datos) {
        return new EventoTiempoReal(tipo, grupoId, Instant.now(), Map.copyOf(datos));
    }
}
