package com.huecko.backend.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra los secretos TOTP antes de guardarlos (AES-256-GCM). Con una copia de
 * la base no se pueden generar códigos: haría falta también la clave, que vive
 * solo en las variables de entorno del servidor.
 *
 * La clave se deriva de HUECKO_JWT_SECRET con SHA-256 y una etiqueta propia,
 * para no añadir otra variable que configurar. Si se cambia ese secreto, los
 * secretos guardados dejan de poder leerse y cada cuenta tendría que volver a
 * activar la verificación en dos pasos.
 */
@Component
public class CifradoSecretos {

    private static final String ETIQUETA = "huecko/dos-pasos/v1:";
    private static final int BYTES_IV = 12;
    private static final int BITS_ETIQUETA_GCM = 128;
    private static final SecureRandom AZAR = new SecureRandom();

    private final SecretKeySpec clave;

    public CifradoSecretos(@Value("${huecko.jwt.secret}") String secretoBase) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((ETIQUETA + secretoBase).getBytes(StandardCharsets.UTF_8));
            this.clave = new SecretKeySpec(bytes, "AES");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    /** `iv || texto cifrado` en Base64. */
    public String cifrar(String texto) {
        try {
            byte[] iv = new byte[BYTES_IV];
            AZAR.nextBytes(iv);
            Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
            cifrador.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(BITS_ETIQUETA_GCM, iv));
            byte[] cifrado = cifrador.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo cifrar el secreto", ex);
        }
    }

    public String descifrar(String guardado) {
        try {
            ByteBuffer datos = ByteBuffer.wrap(Base64.getDecoder().decode(guardado));
            byte[] iv = new byte[BYTES_IV];
            datos.get(iv);
            byte[] cifrado = new byte[datos.remaining()];
            datos.get(cifrado);
            Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
            cifrador.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(BITS_ETIQUETA_GCM, iv));
            return new String(cifrador.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("No se pudo leer el secreto de la verificación en dos pasos", ex);
        }
    }
}
