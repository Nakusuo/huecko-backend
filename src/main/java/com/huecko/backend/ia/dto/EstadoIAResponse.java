package com.huecko.backend.ia.dto;

/**
 * Si conviene ofrecer las funciones de IA. Es una pista para la interfaz, no
 * una garantía: el estado se comprueba cada 10 minutos (VigiaIA) y cada
 * llamada puede fallar igual.
 *
 * @param estado DESACTIVADA, SIN_COMPROBAR, ACTIVA, SIN_GEMINI o SIN_RESPUESTA
 */
public record EstadoIAResponse(boolean disponible, String estado) {
}
