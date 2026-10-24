package com.huecko.backend.ia.dto;

import java.util.List;

/**
 * Ideas de plan para el grupo. No se guardan: «Usar» abre «Proponer plan» con
 * la idea ya escrita.
 *
 * @param ideas de 1 a 3
 */
public record IdeasPlanResponse(List<Idea> ideas) {

    /**
     * @param categoria ESTUDIO, REUNION, COMIDA, DEPORTE, SALIDA u OTRO
     * @param lugar un tipo de lugar o uno concreto; null si no aplica
     * @param ventana dentro de un hueco real del grupo, o null si la IA no eligió ninguno válido
     * @param razon por qué encaja, en una frase
     */
    public record Idea(String titulo, String categoria, String lugar, BorradorPlanResponse.Ventana ventana,
                       String razon) {
    }
}
