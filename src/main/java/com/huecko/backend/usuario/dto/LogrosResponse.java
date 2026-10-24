package com.huecko.backend.usuario.dto;

import java.util.List;

/**
 * Tus números, solo para ti. A los demás les llegan únicamente las insignias
 * (ver PerfilPublicoResponse).
 *
 * @param planesVividos confirmados de tus grupos que ya empezaron y a los que no avisaste que faltarías
 * @param aTiempo       de esos, a los que no avisaste de un retraso
 * @param propuestos    planes que propusiste y no se cancelaron
 */
public record LogrosResponse(int planesVividos, int aTiempo, int propuestos, List<String> insignias) {
}
