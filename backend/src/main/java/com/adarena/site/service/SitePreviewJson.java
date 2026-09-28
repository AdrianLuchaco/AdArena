package com.adarena.site.service;

import com.adarena.site.dto.SiteDtos.Showcase;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

/** Conversión de las columnas JSON de las webs (galería, frases) y de la presentación congelada del ganador. */
@Component
public class SitePreviewJson {

    /** Una foto de la galería: de qué dirección salió y con qué id la guardamos. */
    public record GalleryImage(String src, UUID imageId) {
    }

    private static final TypeReference<List<GalleryImage>> GALLERY = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final JsonMapper mapper;

    public SitePreviewJson(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public String writeGallery(List<GalleryImage> gallery) {
        return mapper.writeValueAsString(gallery);
    }

    public List<GalleryImage> readGallery(String json) {
        return read(json, GALLERY);
    }

    public String writeHighlights(List<String> highlights) {
        return mapper.writeValueAsString(highlights);
    }

    public List<String> readHighlights(String json) {
        return read(json, STRINGS);
    }

    public String writeShowcase(Showcase showcase) {
        return showcase == null ? null : mapper.writeValueAsString(showcase);
    }

    public Showcase readShowcase(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, Showcase.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    private <T> List<T> read(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, type);
        } catch (JacksonException e) {
            return List.of();
        }
    }
}
