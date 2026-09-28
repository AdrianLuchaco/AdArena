package com.adarena.image.controller;

import com.adarena.common.error.ApiException;
import com.adarena.image.domain.StoredImage;
import com.adarena.image.dto.ImageResponse;
import com.adarena.image.service.ImageService;
import com.adarena.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Tag(name = "Imágenes")
@RestController
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @Operation(summary = "Subir una imagen",
            description = "JPG, PNG, WebP o GIF de hasta 5 MB. Se reduce a 1600 px y se vuelve a codificar sin metadatos.")
    @PostMapping(path = "/api/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageResponse> upload(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "You haven't selected an image.");
        }
        StoredImage image = imageService.store(CurrentUser.id(jwt), file.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(ImageResponse.from(image));
    }

    /**
     * Sirve una imagen. Son inmutables (cambiar el logo crea otra imagen), así que el navegador
     * puede guardarlas en caché un año entero.
     */
    @Operation(summary = "Descargar una imagen (público)")
    @GetMapping("/api/public/images/{id}")
    public ResponseEntity<byte[]> download(@PathVariable UUID id,
                                           @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        StoredImage image = imageService.get(id);
        String etag = "\"" + image.getSha256() + "\"";
        CacheControl cache = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .contentLength(image.getSizeBytes())
                .eTag(etag)
                .cacheControl(cache)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(image.getData());
    }
}
