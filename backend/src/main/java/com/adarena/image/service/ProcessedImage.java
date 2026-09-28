package com.adarena.image.service;

/** Imagen ya re-codificada, lista para guardar. */
public record ProcessedImage(byte[] data, String contentType, int width, int height) {
}
