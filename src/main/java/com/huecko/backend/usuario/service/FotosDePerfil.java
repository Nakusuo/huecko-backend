package com.huecko.backend.usuario.service;

import com.huecko.backend.common.exception.BusinessException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Rehace la foto de perfil antes de guardarla. Nunca se guarda el archivo tal
 * como llega:
 *  - una foto de celular lleva metadatos (EXIF) con las coordenadas GPS de
 *    donde se tomó; al decodificarla y volver a escribirla no queda ninguno;
 *  - algo que solo «parece» una imagen (un HTML con la cabecera de un PNG) no
 *    sobrevive a ese viaje de ida y vuelta;
 *  - el resultado es siempre un JPEG cuadrado de 256 px, unos 20 KB.
 *
 * El frontend ya manda la foto reducida, pero eso no se puede dar por hecho:
 * cualquiera puede llamar a la API con otro archivo.
 */
final class FotosDePerfil {

    static final int LADO = 256;
    /** Más que esto no es una foto de perfil razonable. */
    static final int LADO_MAXIMO = 8000;
    /**
     * Lado al que se decodifica como mucho, saltando píxeles: una imagen de
     * 8000 px entera ocuparía 256 MB en memoria, más de lo que tiene la API en
     * el plan gratuito.
     */
    private static final int LADO_DE_LECTURA = 1024;
    private static final float CALIDAD = 0.85f;
    private static final String NO_SE_LEE = "No se pudo leer la imagen. Prueba con otra foto en JPG o PNG.";

    private FotosDePerfil() {
    }

    static byte[] normalizar(byte[] original) {
        if (original == null || original.length == 0) {
            throw new BusinessException("La foto está vacía.");
        }
        if (!esJpeg(original) && !esPng(original)) {
            throw new BusinessException("La foto tiene que ser JPG o PNG.");
        }
        return comoJpeg(cuadrada(leer(original)));
    }

    private static BufferedImage leer(byte[] bytes) {
        try (ImageInputStream entrada = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                throw new BusinessException(NO_SE_LEE);
            }
            ImageReader lector = lectores.next();
            try {
                // `ignoreMetadata`: ni siquiera se leen los metadatos.
                lector.setInput(entrada, true, true);
                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);
                if (ancho > LADO_MAXIMO || alto > LADO_MAXIMO) {
                    throw new BusinessException("La foto es demasiado grande: como mucho " + LADO_MAXIMO + " px de lado.");
                }
                ImageReadParam parametros = lector.getDefaultReadParam();
                int paso = Math.max(1, Math.max(ancho, alto) / LADO_DE_LECTURA);
                parametros.setSourceSubsampling(paso, paso, 0, 0);
                BufferedImage imagen = lector.read(0, parametros);
                if (imagen == null || imagen.getWidth() == 0 || imagen.getHeight() == 0) {
                    throw new BusinessException(NO_SE_LEE);
                }
                return imagen;
            } finally {
                lector.dispose();
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            // Un archivo corrupto puede romper el decodificador de mil maneras.
            throw new BusinessException(NO_SE_LEE);
        }
    }

    /**
     * Recorta al centro y reduce a LADO x LADO, sobre blanco: un PNG con
     * transparencia saldría con el fondo negro en JPEG.
     */
    private static BufferedImage cuadrada(BufferedImage imagen) {
        int recorte = Math.min(imagen.getWidth(), imagen.getHeight());
        int x = (imagen.getWidth() - recorte) / 2;
        int y = (imagen.getHeight() - recorte) / 2;

        BufferedImage actual = imagen.getSubimage(x, y, recorte, recorte);
        int lado = recorte;
        // Reducir de golpe a 256 px desde 1000 deja la foto granulada; a mitades
        // el resultado es suave sin el coste del promedio por áreas.
        do {
            lado = Math.max(LADO, lado / 2);
            actual = escalar(actual, lado);
        } while (lado > LADO);
        return actual;
    }

    private static BufferedImage escalar(BufferedImage origen, int lado) {
        BufferedImage destino = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, lado, lado);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(origen, 0, 0, lado, lado, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    private static byte[] comoJpeg(BufferedImage imagen) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream destino = new MemoryCacheImageOutputStream(salida)) {
            escritor.setOutput(destino);
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(CALIDAD);
            // Sin metadatos: la imagen nueva no hereda nada del archivo original.
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo codificar la foto", ex);
        } finally {
            escritor.dispose();
        }
        return salida.toByteArray();
    }

    static boolean esJpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    static boolean esPng(byte[] b) {
        return b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
    }
}
