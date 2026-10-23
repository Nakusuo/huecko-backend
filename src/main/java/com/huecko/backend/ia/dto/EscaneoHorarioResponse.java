package com.huecko.backend.ia.dto;

import java.util.List;

/**
 * Lo que la IA leyó de la foto. No se guarda nada: el frontend lo enseña para
 * revisarlo y crea los bloques confirmados por la vía normal.
 *
 * @param bloques en orden de la semana; vacío si la foto no era un horario
 */
public record EscaneoHorarioResponse(List<Bloque> bloques) {

    /**
     * @param diaSemana 1 (lunes) a 7 (domingo)
     * @param horaInicio HH:mm
     * @param horaFin HH:mm, posterior al inicio
     * @param dudoso la IA tuvo que adivinar el día o la hora: conviene revisarlo
     */
    public record Bloque(int diaSemana, String horaInicio, String horaFin, String titulo, boolean dudoso) {
    }
}
