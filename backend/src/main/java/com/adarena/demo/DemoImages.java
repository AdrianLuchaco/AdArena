package com.adarena.demo;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Dibuja imágenes de ejemplo para los anunciantes de demostración: degradado de color, luces
 * difuminadas y una forma sencilla a modo de "logo". No usa fuentes de texto (el servidor en
 * Docker puede no tenerlas instaladas); el nombre de la empresa lo pinta la web encima.
 */
final class DemoImages {

    static final int WIDTH = 1200;
    static final int HEIGHT = 800;

    enum Mark { RING, SQUARE, TRIANGLE, WAVE }

    private DemoImages() {
    }

    static BufferedImage render(Color from, Color to, Mark mark, long seed) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            g.setPaint(new GradientPaint(0, 0, from, WIDTH, HEIGHT, to));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            Random random = new Random(seed);
            for (int i = 0; i < 16; i++) {
                int diameter = 90 + random.nextInt(380);
                int x = random.nextInt(WIDTH) - diameter / 2;
                int y = random.nextInt(HEIGHT) - diameter / 2;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.06f + random.nextFloat() * 0.12f));
                g.setColor(Color.WHITE);
                g.fillOval(x, y, diameter, diameter);
            }

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));
            drawMark(g, mark, WIDTH / 2, HEIGHT / 2, 1.0);
        } finally {
            g.dispose();
        }
        return image;
    }

    /** Logo cuadrado (256 × 256) con el mismo dibujo: para la presentación del ganador. */
    static BufferedImage renderIcon(Color from, Color to, Mark mark) {
        int size = 256;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, from, size, size, to));
            g.fill(new RoundRectangle2D.Double(0, 0, size, size, 64, 64));
            g.setColor(Color.WHITE);
            drawMark(g, mark, size / 2, size / 2, 0.52);
        } finally {
            g.dispose();
        }
        return image;
    }

    /** "Foto" de ambiente sin logo (degradado y formas): para la galería de la presentación. */
    static BufferedImage renderScene(Color from, Color to, long seed) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Random random = new Random(seed);
            boolean flip = random.nextBoolean();
            g.setPaint(new GradientPaint(flip ? WIDTH : 0, 0, to, flip ? 0 : WIDTH, HEIGHT, from));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            for (int i = 0; i < 28; i++) {
                int diameter = 60 + random.nextInt(520);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.05f + random.nextFloat() * 0.16f));
                g.setColor(random.nextInt(4) == 0 ? from.darker() : Color.WHITE);
                g.fillOval(random.nextInt(WIDTH) - diameter / 2, random.nextInt(HEIGHT) - diameter / 2, diameter, diameter);
            }
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f));
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(48, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int band = 0; band < 3; band++) {
                int offset = random.nextInt(WIDTH);
                g.drawLine(offset - 400, HEIGHT + 60, offset + 400, -60);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void drawMark(Graphics2D g, Mark mark, int cx, int cy, double scale) {
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke((float) (26 * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (mark) {
            case RING -> {
                g.drawOval(cx - s(130, scale), cy - s(130, scale), s(260, scale), s(260, scale));
                g.fillOval(cx - s(48, scale), cy - s(48, scale), s(96, scale), s(96, scale));
            }
            case SQUARE -> {
                g.draw(new RoundRectangle2D.Double(cx - 125 * scale, cy - 125 * scale, 250 * scale, 250 * scale,
                        70 * scale, 70 * scale));
                g.fill(new RoundRectangle2D.Double(cx - 45 * scale, cy - 45 * scale, 90 * scale, 90 * scale,
                        26 * scale, 26 * scale));
            }
            case TRIANGLE -> {
                Path2D triangle = new Path2D.Double();
                triangle.moveTo(cx, cy - 140 * scale);
                triangle.lineTo(cx + 150 * scale, cy + 110 * scale);
                triangle.lineTo(cx - 150 * scale, cy + 110 * scale);
                triangle.closePath();
                g.draw(triangle);
                g.fillOval(cx - s(36, scale), cy + s(8, scale), s(72, scale), s(72, scale));
            }
            case WAVE -> {
                for (int row = -1; row <= 1; row++) {
                    Path2D wave = new Path2D.Double();
                    double y = cy + row * 70 * scale;
                    wave.moveTo(cx - 170 * scale, y);
                    wave.curveTo(cx - 85 * scale, y - 60 * scale, cx - 85 * scale, y + 60 * scale, cx, y);
                    wave.curveTo(cx + 85 * scale, y - 60 * scale, cx + 85 * scale, y + 60 * scale, cx + 170 * scale, y);
                    g.draw(wave);
                }
            }
        }
    }

    private static int s(int value, double scale) {
        return (int) Math.round(value * scale);
    }
}
