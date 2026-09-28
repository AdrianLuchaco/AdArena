package com.adarena.site.extract;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * El color "de marca" de una imagen: la media de sus píxeles con color (los grises, blancos y
 * negros casi no cuentan). Se usa cuando la web no declara su color (&lt;meta name="theme-color"&gt;).
 */
public final class DominantColor {

    private static final int GRID = 32;

    private DominantColor() {
    }

    /** @return "#1f7a5a", o null si la imagen es casi gris o no se puede leer */
    public static String of(byte[] image) {
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(image));
        } catch (IOException e) {
            return null;
        }
        if (source == null) {
            return null;
        }
        double red = 0;
        double green = 0;
        double blue = 0;
        double weights = 0;
        float[] hsb = new float[3];
        for (int gy = 0; gy < GRID; gy++) {
            for (int gx = 0; gx < GRID; gx++) {
                int argb = source.getRGB(gx * source.getWidth() / GRID, gy * source.getHeight() / GRID);
                if ((argb >>> 24) < 200) {
                    continue;  // transparente
                }
                int r = argb >> 16 & 0xff;
                int g = argb >> 8 & 0xff;
                int b = argb & 0xff;
                Color.RGBtoHSB(r, g, b, hsb);
                // Cuenta más cuanto más color tiene y menos si es casi negro o casi blanco
                double weight = hsb[1] * hsb[1] * Math.min(hsb[2], 1 - Math.abs(hsb[2] - 0.55)) + 0.001;
                red += r * weight;
                green += g * weight;
                blue += b * weight;
                weights += weight;
            }
        }
        if (weights <= 0) {
            return null;
        }
        int r = (int) Math.round(red / weights);
        int g = (int) Math.round(green / weights);
        int b = (int) Math.round(blue / weights);
        Color.RGBtoHSB(r, g, b, hsb);
        if (hsb[1] < 0.18) {
            return null;
        }
        return String.format("#%02x%02x%02x", r, g, b);
    }
}
