package com.huecko.backend.ia.dto;

/**
 * Cómo va la votación de un plan, según Huecko IA. Solo informa: no cambia
 * nada del plan.
 *
 * @param recomendacion CERRAR, ESPERAR, MANTENER o REPROGRAMAR, siempre una
 *                      que el estado del plan admite; null si la IA propuso otra
 * @param razon por qué, en una frase; null si no hay recomendación
 */
public record ResumenVotacionResponse(String resumen, String recomendacion, String razon) {
}
