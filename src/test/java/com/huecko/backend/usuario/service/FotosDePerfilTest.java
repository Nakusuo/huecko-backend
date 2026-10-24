package com.huecko.backend.usuario.service;

import com.huecko.backend.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El servidor rehace cada foto de perfil: tamaño fijo, sin metadatos y solo si de verdad es una imagen. */
class FotosDePerfilTest {

    private static byte[] codificar(BufferedImage imagen, String formato) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, formato, salida);
        return salida.toByteArray();
    }

    private static BufferedImage leer(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private static BufferedImage rectangulo(int ancho, int alto) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, ancho, alto);
        g.dispose();
        return imagen;
    }

    @Test
    @DisplayName("Una foto grande y apaisada sale como JPEG cuadrado de 256 px")
    void cuadradaYPequena() throws IOException {
        byte[] resultado = FotosDePerfil.normalizar(codificar(rectangulo(1600, 900), "png"));

        assertThat(FotosDePerfil.esJpeg(resultado)).isTrue();
        BufferedImage imagen = leer(resultado);
        assertThat(imagen.getWidth()).isEqualTo(FotosDePerfil.LADO);
        assertThat(imagen.getHeight()).isEqualTo(FotosDePerfil.LADO);
        assertThat(resultado.length).isLessThan(30_000);
    }

    @Test
    @DisplayName("Los metadatos del original, como el GPS de una foto de celular, no llegan a la foto guardada")
    void sinMetadatos() throws IOException {
        byte[] original = codificar(rectangulo(400, 400), "jpg");
        byte[] exif = ("Exif\0\0MM\0*\0\0\0\b\0\0\0\0\0\0GPS-12.0464,-77.0428").getBytes(StandardCharsets.ISO_8859_1);
        // Segmento APP1 justo después del SOI, como lo escriben las cámaras.
        ByteArrayOutputStream conExif = new ByteArrayOutputStream();
        conExif.write(original, 0, 2);
        conExif.write(0xFF);
        conExif.write(0xE1);
        conExif.write((exif.length + 2) >> 8);
        conExif.write((exif.length + 2) & 0xFF);
        conExif.write(exif);
        conExif.write(original, 2, original.length - 2);
        assertThat(new String(conExif.toByteArray(), StandardCharsets.ISO_8859_1)).contains("GPS-12.0464");

        String resultado = new String(FotosDePerfil.normalizar(conExif.toByteArray()), StandardCharsets.ISO_8859_1);

        assertThat(resultado).doesNotContain("GPS-12.0464").doesNotContain("Exif");
    }

    @Test
    @DisplayName("Un PNG transparente queda sobre blanco, no sobre negro")
    void transparenteSobreBlanco() throws IOException {
        BufferedImage transparente = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);

        BufferedImage imagen = leer(FotosDePerfil.normalizar(codificar(transparente, "png")));

        Color esquina = new Color(imagen.getRGB(0, 0));
        assertThat(esquina.getRed()).isGreaterThan(240);
        assertThat(esquina.getGreen()).isGreaterThan(240);
        assertThat(esquina.getBlue()).isGreaterThan(240);
    }

    @Test
    @DisplayName("Lo que no es JPG ni PNG se rechaza, aunque empiece como un PNG")
    void soloImagenesDeVerdad() {
        assertThatThrownBy(() -> FotosDePerfil.normalizar("hola".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("JPG o PNG");

        byte[] falso = new byte[64];
        System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 0, falso, 0, 8);
        byte[] html = "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(html, 0, falso, 8, html.length);
        assertThatThrownBy(() -> FotosDePerfil.normalizar(falso))
                .isInstanceOf(BusinessException.class).hasMessageContaining("No se pudo leer");

        assertThatThrownBy(() -> FotosDePerfil.normalizar(new byte[0])).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Una imagen enorme se rechaza antes de decodificarla")
    void demasiadoGrande() throws IOException {
        byte[] enorme = codificar(rectangulo(FotosDePerfil.LADO_MAXIMO + 1, 4), "png");

        assertThatThrownBy(() -> FotosDePerfil.normalizar(enorme))
                .isInstanceOf(BusinessException.class).hasMessageContaining("demasiado grande");
    }

    @Test
    @DisplayName("Una foto más pequeña que 256 px se amplía para que todas tengan el mismo tamaño")
    void pequenaSeAmplia() throws IOException {
        BufferedImage imagen = leer(FotosDePerfil.normalizar(codificar(rectangulo(120, 90), "jpg")));

        assertThat(imagen.getWidth()).isEqualTo(FotosDePerfil.LADO);
    }
}
