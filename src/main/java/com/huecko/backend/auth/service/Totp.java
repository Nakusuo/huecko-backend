package com.huecko.backend.auth.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * Códigos de un solo uso basados en el tiempo (TOTP, RFC 6238), los mismos que
 * generan Google Authenticator, Microsoft Authenticator o Authy: HMAC-SHA1,
 * 6 dígitos, intervalos de 30 segundos.
 *
 * Sin dependencias: son unas pocas líneas y así se ve cómo funciona.
 */
public final class Totp {

    static final int DIGITOS = 6;
    static final long PASO_SEGUNDOS = 30;
    /** Bytes del secreto: 160 bits, lo que recomienda el RFC 4226. */
    static final int BYTES_SECRETO = 20;
    /**
     * Intervalos de margen a cada lado. Con 1, el código vale de 30 a 90
     * segundos: cubre que el reloj del móvil vaya algo adelantado o atrasado.
     */
    static final int MARGEN = 1;

    private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom AZAR = new SecureRandom();

    private Totp() {
    }

    /** Secreto nuevo en Base32, que es como lo leen las apps (y como se teclea a mano). */
    public static String nuevoSecreto() {
        byte[] bytes = new byte[BYTES_SECRETO];
        AZAR.nextBytes(bytes);
        return base32(bytes);
    }

    /** El intervalo de 30 s al que pertenece un instante. */
    public static long paso(Instant momento) {
        return Math.floorDiv(momento.getEpochSecond(), PASO_SEGUNDOS);
    }

    /** El código de 6 dígitos de un intervalo. */
    public static String codigo(String secretoBase32, long paso) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(desdeBase32(secretoBase32), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(paso).array());
            // Truncado dinámico (RFC 4226, 5.3): 4 bytes a partir del desplazamiento
            // que marcan los 4 bits bajos del último byte.
            int desplazamiento = hash[hash.length - 1] & 0x0f;
            int binario = ((hash[desplazamiento] & 0x7f) << 24)
                    | ((hash[desplazamiento + 1] & 0xff) << 16)
                    | ((hash[desplazamiento + 2] & 0xff) << 8)
                    | (hash[desplazamiento + 3] & 0xff);
            return String.format("%0" + DIGITOS + "d", binario % 1_000_000);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA1 no disponible", ex);
        }
    }

    /**
     * El intervalo en el que `codigo` es válido, buscando alrededor de `ahora`.
     * Vacío si no coincide o si ese intervalo no es posterior a
     * `ultimoPasoUsado`: un código que ya sirvió no vuelve a servir.
     */
    public static OptionalLong verificar(String secretoBase32, String codigo, Instant ahora, Long ultimoPasoUsado) {
        if (codigo == null || !codigo.matches("\\d{" + DIGITOS + "}")) {
            return OptionalLong.empty();
        }
        long actual = paso(ahora);
        for (long paso = actual - MARGEN; paso <= actual + MARGEN; paso++) {
            if (ultimoPasoUsado != null && paso <= ultimoPasoUsado) {
                continue;
            }
            if (iguales(codigo(secretoBase32, paso), codigo)) {
                return OptionalLong.of(paso);
            }
        }
        return OptionalLong.empty();
    }

    /**
     * Dirección `otpauth://` del QR (formato de Google Authenticator): la app la
     * lee y añade la cuenta con el nombre del servicio y el correo.
     */
    public static String uri(String emisor, String cuenta, String secretoBase32) {
        String etiqueta = codificar(emisor) + ":" + codificar(cuenta);
        return "otpauth://totp/" + etiqueta + "?secret=" + secretoBase32 + "&issuer=" + codificar(emisor)
                + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PASO_SEGUNDOS;
    }

    /** Comparación en tiempo constante, para no dar pistas por lo que tarda. */
    private static boolean iguales(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String base32(byte[] bytes) {
        StringBuilder salida = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                salida.append(ALFABETO.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            salida.append(ALFABETO.charAt((buffer << (5 - bits)) & 31));
        }
        return salida.toString();
    }

    static byte[] desdeBase32(String texto) {
        String limpio = texto.replace(" ", "").replace("=", "").toUpperCase();
        ByteBuffer salida = ByteBuffer.allocate(limpio.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : limpio.toCharArray()) {
            int valor = ALFABETO.indexOf(c);
            if (valor < 0) {
                throw new IllegalArgumentException("Carácter no válido en Base32: " + c);
            }
            buffer = (buffer << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                salida.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return salida.array();
    }
}
