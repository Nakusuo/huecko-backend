package com.huecko.backend.common;

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
 * Rehace las fotos que sube la gente antes de guardarlas. Nunca se guarda el
 * archivo tal como llega:
 *  - una foto de celular lleva metadatos (EXIF) con las coordenadas GPS de
 *    donde se tomó; al decodificarla y volver a escribirla no queda ninguno;
 *  - algo que solo «parece» una imagen (un HTML con la cabecera de un PNG) no
 *    sobrevive a ese viaje de ida y vuelta;
 *  - el tamaño queda acotado, sea cual sea el original.
 *
 * El frontend ya manda las fotos reducidas, pero eso no se puede dar por
 * hecho: cualquiera puede llamar a la API con otro archivo.
 */
public final class Imagenes {

    /** Foto de perfil: cuadrada. */
    public static final int LADO_PERFIL = 256;
    /** Foto de un álbum: el lado largo, sin recortar. */
    public static final int LADO_RECUERDO = 1280;
    /** Miniatura de un álbum: cuadrada, para la cuadrícula. */
    public static final int LADO_MINIATURA = 320;
    /** Más que esto no es una foto razonable. */
    public static final int LADO_MAXIMO = 8000;

    private static final float CALIDAD = 0.85f;
    private static final String NO_SE_LEE = "No se pudo leer la imagen. Prueba con otra foto en JPG o PNG.";

    /** Una foto de álbum ya rehecha, con su miniatura. */
    public record Recuerdo(byte[] foto, int ancho, int alto, byte[] miniatura) {
    }

    private Imagenes() {
    }

    /** JPEG cuadrado de 256 px, recortado al centro. */
    public static byte[] perfil(byte[] original) {
        // Se decodifica a 1024 px como mucho: una imagen de 8000 px entera
        // ocuparía 256 MB en memoria, más de lo que tiene la API en Render.
        return jpeg(cuadrada(leer(original, 1024), LADO_PERFIL));
    }

    /** La foto entera con el lado largo a 1280 px, y una miniatura cuadrada de 320. */
    public static Recuerdo recuerdo(byte[] original) {
        BufferedImage imagen = leer(original, LADO_RECUERDO * 2);
        BufferedImage ajustada = dentroDe(imagen, LADO_RECUERDO);
        return new Recuerdo(jpeg(ajustada), ajustada.getWidth(), ajustada.getHeight(),
                jpeg(cuadrada(imagen, LADO_MINIATURA)));
    }

    private static BufferedImage leer(byte[] bytes, int ladoDeLectura) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("La foto está vacía.");
        }
        if (!esJpeg(bytes) && !esPng(bytes)) {
            throw new BusinessException("La foto tiene que ser JPG o PNG.");
        }
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
                int paso = Math.max(1, Math.max(ancho, alto) / ladoDeLectura);
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

    /** Recorta al centro y deja un cuadrado de `lado` px. */
    private static BufferedImage cuadrada(BufferedImage imagen, int lado) {
        int recorte = Math.min(imagen.getWidth(), imagen.getHeight());
        BufferedImage centro = imagen.getSubimage((imagen.getWidth() - recorte) / 2,
                (imagen.getHeight() - recorte) / 2, recorte, recorte);
        return reducir(centro, lado, lado);
    }

    /** Que el lado largo no pase de `maximo`, sin recortar ni ampliar. */
    private static BufferedImage dentroDe(BufferedImage imagen, int maximo) {
        int largo = Math.max(imagen.getWidth(), imagen.getHeight());
        if (largo <= maximo) {
            return reducir(imagen, imagen.getWidth(), imagen.getHeight());
        }
        double escala = (double) maximo / largo;
        return reducir(imagen, Math.max(1, (int) Math.round(imagen.getWidth() * escala)),
                Math.max(1, (int) Math.round(imagen.getHeight() * escala)));
    }

    /**
     * Reducir de golpe desde 2000 px deja la foto granulada; a mitades el
     * resultado es suave sin el coste del promedio por áreas. Siempre acaba
     * en una imagen RGB nueva sobre blanco: un PNG con transparencia saldría
     * con el fondo negro en JPEG.
     */
    private static BufferedImage reducir(BufferedImage origen, int ancho, int alto) {
        BufferedImage actual = origen;
        int w = origen.getWidth();
        int h = origen.getHeight();
        do {
            w = Math.max(ancho, w / 2);
            h = Math.max(alto, h / 2);
            actual = dibujar(actual, w, h);
        } while (w > ancho || h > alto);
        return actual;
    }

    private static BufferedImage dibujar(BufferedImage origen, int ancho, int alto) {
        BufferedImage destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, ancho, alto);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(origen, 0, 0, ancho, alto, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    private static byte[] jpeg(BufferedImage imagen) {
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

    public static boolean esJpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    public static boolean esPng(byte[] b) {
        return b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
    }
}
