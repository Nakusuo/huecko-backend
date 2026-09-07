package com.huecko.backend.grupo.dto;

/**
 * Una casilla del heatmap: un día de la semana y una franja de una hora.
 *
 * RNF-02: aquí solo viaja el RECUENTO de quién está libre, nunca la etiqueta
 * del bloque que ocupa a nadie. El heatmap no puede filtrar que alguien tiene
 * "Terapia" los martes.
 */
public record CeldaDisponibilidadResponse(
        /** 1 = lunes … 7 = domingo (ISO-8601, igual que BloqueHorario.diaSemana). */
        int diaSemana,
        /** Hora de inicio de la franja: 14 significa 14:00–15:00. */
        int hora,
        int disponibles,
        int totalMiembros,
        int porcentaje,
        boolean cumpleUmbral
) {
}
