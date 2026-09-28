package com.adarena.image.service;

import com.adarena.common.error.ApiException;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * "Sanea" las imágenes que suben los usuarios. Nunca guardamos ni servimos el archivo original:
 * <ol>
 *   <li>Detectamos el formato por su CONTENIDO (no por el nombre ni por lo que diga el navegador).
 *       Se aceptan JPG, PNG, WebP y GIF (solo el primer fotograma).</li>
 *   <li>Leemos solo la cabecera para conocer el tamaño y rechazar "bombas" (imágenes de pocos KB
 *       que ocupan gigas al abrirse).</li>
 *   <li>Decodificamos a resolución reducida si es enorme (poca memoria) y la escalamos a 1600 px
 *       como máximo por lado.</li>
 *   <li>La volvemos a codificar desde cero: PNG si tiene transparencia y JPEG en otro caso. Así
 *       desaparecen los metadatos (GPS de la foto, autor…) y cualquier contenido oculto o malicioso.</li>
 * </ol>
 */
@Component
public class ImageProcessor {

    public static final int MAX_OUTPUT_SIDE = 1600;
    public static final int MIN_SOURCE_SIDE = 64;
    public static final int MAX_SOURCE_SIDE = 12_000;
    public static final long MAX_SOURCE_PIXELS = 100_000_000L;
    public static final int MAX_OUTPUT_BYTES = 2 * 1024 * 1024;

    private static final Set<String> ALLOWED_FORMATS = Set.of("png", "jpeg", "jpg", "webp", "gif");
    private static final float JPEG_QUALITY = 0.86f;
    private static final int MIN_RETRY_SIDE = 400;

    public ProcessedImage process(byte[] input) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw invalidImage();
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!ALLOWED_FORMATS.contains(format)) {
                    throw invalidImage();
                }
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                checkDimensions(width, height);

                ImageReadParam param = reader.getDefaultReadParam();
                int subsampling = Math.max(1, Math.max(width, height) / (MAX_OUTPUT_SIDE * 2));
                if (subsampling > 1) {
                    param.setSourceSubsampling(subsampling, subsampling, 0, 0);
                }
                return encode(reader.read(0, param));
            } finally {
                reader.dispose();
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw invalidImage();
        }
    }

    /** Escala y codifica una imagen ya en memoria (también la usan las imágenes de demostración). */
    public ProcessedImage encode(BufferedImage source) {
        boolean transparent = hasTransparency(source);
        int maxSide = MAX_OUTPUT_SIDE;
        while (true) {
            BufferedImage scaled = scaleToFit(source, maxSide, transparent);
            byte[] data = transparent ? writePng(scaled) : writeJpeg(scaled);
            if (data.length <= MAX_OUTPUT_BYTES) {
                return new ProcessedImage(data, transparent ? "image/png" : "image/jpeg",
                        scaled.getWidth(), scaled.getHeight());
            }
            if (maxSide <= MIN_RETRY_SIDE) {
                throw ApiException.badRequest("IMAGE_TOO_COMPLEX",
                        "We couldn't shrink the image enough. Try a simpler one.");
            }
            maxSide = maxSide * 3 / 4;
        }
    }

    private static void checkDimensions(int width, int height) {
        if (width < MIN_SOURCE_SIDE || height < MIN_SOURCE_SIDE) {
            throw ApiException.badRequest("IMAGE_TOO_SMALL",
                    "The image is too small. It must be at least 64 × 64 pixels.");
        }
        if (width > MAX_SOURCE_SIDE || height > MAX_SOURCE_SIDE || (long) width * height > MAX_SOURCE_PIXELS) {
            throw ApiException.badRequest("IMAGE_DIMENSIONS_TOO_LARGE",
                    "The image dimensions are too large.");
        }
    }

    private static BufferedImage scaleToFit(BufferedImage source, int maxSide, boolean transparent) {
        int width = source.getWidth();
        int height = source.getHeight();
        double ratio = Math.min(1.0, (double) maxSide / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));

        // Reducción por pasos (a la mitad cada vez): mucha más calidad que de golpe
        BufferedImage current = source;
        int currentWidth = width;
        int currentHeight = height;
        while (currentWidth / 2 >= targetWidth && currentHeight / 2 >= targetHeight) {
            currentWidth /= 2;
            currentHeight /= 2;
            current = draw(current, currentWidth, currentHeight, transparent);
        }
        // El último dibujo siempre se hace sobre un lienzo nuevo y limpio
        return draw(current, targetWidth, targetHeight, transparent);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height, boolean transparent) {
        BufferedImage target = new BufferedImage(width, height,
                transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            if (!transparent) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
            }
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /** ¿Tiene algún píxel transparente de verdad? (Muchos PNG tienen canal alfa sin usarlo.) */
    private static boolean hasTransparency(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return false;
        }
        int step = Math.max(1, (int) Math.sqrt((double) image.getWidth() * image.getHeight() / 1_000_000));
        for (int y = 0; y < image.getHeight(); y += step) {
            for (int x = 0; x < image.getWidth(); x += step) {
                if ((image.getRGB(x, y) >>> 24) < 255) {
                    return true;
                }
            }
        }
        return false;
    }

    private static byte[] writePng(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", out)) {
                throw new IllegalStateException("No PNG writer available");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] writeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ImageOutputStream imageOut = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(imageOut);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
            imageOut.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
    }

    private static ApiException invalidImage() {
        return ApiException.badRequest("INVALID_IMAGE",
                "That file isn't a valid image. Use a JPG, PNG, WebP or GIF.");
    }
}
