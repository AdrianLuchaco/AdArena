package com.adarena.support;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Genera imágenes pequeñas en memoria para los tests. */
public final class TestImages {

    private TestImages() {
    }

    /** PNG opaco (sin transparencia) de un color. */
    public static byte[] opaquePng(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x6D28D9));
        g.fillRect(0, 0, width, height);
        g.dispose();
        return encode(image, "png");
    }

    /** PNG con la mitad derecha transparente (como un logo recortado). */
    public static byte[] transparentPng(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0xF59E0B));
        g.fillRect(0, 0, width / 2, height);
        g.dispose();
        return encode(image, "png");
    }

    public static byte[] jpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x10B981));
        g.fillOval(0, 0, width, height);
        g.dispose();
        return encode(image, "jpeg");
    }

    private static byte[] encode(BufferedImage image, String format) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
