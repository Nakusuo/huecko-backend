package com.huecko.backend.ia.dto;

import java.util.List;

/**
 * Cuánto se usa Huecko IA, para el panel de administración. Solo cuentas:
 * nada de quién la usó ni de qué escribió.
 *
 * @param dias periodo que cubre (lo que se conserva de LlamadaIA)
 * @param funciones siempre las cuatro, en el mismo orden, aunque alguna no se haya usado
 */
public record UsoIAResponse(int dias, long llamadas, List<PorFuncion> funciones) {

    /**
     * @param tasaExito exitosas / llamadas, entre 0 y 1; null sin llamadas
     * @param msPromedio duración media en milisegundos; null sin llamadas
     */
    public record PorFuncion(String funcion, long llamadas, long exitosas, Double tasaExito, Long msPromedio) {
    }
}
