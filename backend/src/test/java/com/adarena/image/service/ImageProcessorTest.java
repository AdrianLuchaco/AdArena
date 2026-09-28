package com.adarena.image.service;

import com.adarena.common.error.ApiException;
import com.adarena.support.TestImages;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    @Test
    void opaqueImagesAreReencodedAsJpeg() throws IOException {
        ProcessedImage result = processor.process(TestImages.opaquePng(300, 200));

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.width()).isEqualTo(300);
        assertThat(result.height()).isEqualTo(200);
        assertThat(decode(result.data())).isNotNull(); // lo que guardamos es una imagen válida
    }

    @Test
    void transparentLogosStayPng() {
        ProcessedImage result = processor.process(TestImages.transparentPng(256, 256));

        assertThat(result.contentType()).isEqualTo("image/png");
    }

    @Test
    void jpegsAreAccepted() {
        assertThat(processor.process(TestImages.jpeg(400, 400)).contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void largeImagesAreScaledDownKeepingTheAspectRatio() {
        ProcessedImage result = processor.process(TestImages.opaquePng(3200, 1000));

        assertThat(result.width()).isEqualTo(1600);
        assertThat(result.height()).isEqualTo(500);
        assertThat(result.data().length).isLessThanOrEqualTo(ImageProcessor.MAX_OUTPUT_BYTES);
    }

    @Test
    void theOriginalBytesAreNeverKept() {
        byte[] original = TestImages.jpeg(200, 200);

        assertThat(processor.process(original).data()).isNotEqualTo(original);
    }

    @Test
    void rejectsFilesThatAreNotImages() {
        byte[] fake = "<svg onload=alert(1)>no soy una imagen</svg>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> processor.process(fake))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("INVALID_IMAGE");
    }

    @Test
    void rejectsTinyImages() {
        assertThatThrownBy(() -> processor.process(TestImages.opaquePng(32, 32)))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("IMAGE_TOO_SMALL");
    }

    @Test
    void rejectsGiganticDimensionsBeforeDecoding() {
        assertThatThrownBy(() -> processor.process(TestImages.opaquePng(ImageProcessor.MAX_SOURCE_SIDE + 1, 70)))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("IMAGE_DIMENSIONS_TOO_LARGE");
    }

    private static BufferedImage decode(byte[] data) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(data));
    }
}
