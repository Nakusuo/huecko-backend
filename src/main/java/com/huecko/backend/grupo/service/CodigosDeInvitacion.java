package com.huecko.backend.grupo.service;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Códigos para unirse a un grupo: 8 caracteres de un alfabeto sin los que se
 * confunden al dictarlos (ni O ni 0, ni I ni 1). Son 32⁸ ≈ 10¹² combinaciones:
 * adivinar uno a ciegas no es realista, y además los fallos tienen límite.
 */
final class CodigosDeInvitacion {

    static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int LARGO = 8;

    private static final SecureRandom AZAR = new SecureRandom();

    private CodigosDeInvitacion() {
    }

    static String nuevo() {
        StringBuilder codigo = new StringBuilder(LARGO);
        for (int i = 0; i < LARGO; i++) {
            codigo.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
        }
        return codigo.toString();
    }

    /** Como lo escribe la gente: con espacios, guiones o en minúsculas. */
    static String normalizar(String codigo) {
        return codigo == null ? "" : codigo.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
}
