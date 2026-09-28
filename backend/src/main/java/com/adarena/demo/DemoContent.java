package com.adarena.demo;

import java.util.Map;

/**
 * SOLO EN DESARROLLO. Los textos de los datos de ejemplo (en inglés, como la web). También sirve
 * para traducir los datos de ejemplo que ya existían en tu base de datos local en español.
 */
final class DemoContent {

    static final String CAFE = "Specialty coffee roasted every week in central Madrid. Come for a flat white and stay for weekend brunch.";
    static final String BIKES = "Electric and mountain bikes. Daily rentals and a 24-hour express repair shop.";
    static final String STUDIO = "Product photography and professional portraits for your brand. In our studio or at your place.";
    static final String GARDEN = "Seasonal organic fruit and veg boxes, straight from the farm to your door.";

    static final String TASK_YOUTUBE_TITLE = "Example: YouTube's official channel";
    static final String TASK_YOUTUBE_TEXT = "A sample promotion: this is how a YouTube channel looks in Bonus links.";
    static final String TASK_X_TITLE = "Example: X's official profile";
    static final String TASK_X_TEXT = "A sample promotion of an X (Twitter) profile.";
    static final String TASK_GARDEN_TITLE = "Huerta Viva's website";
    static final String TASK_GARDEN_TEXT = "Organic fruit and veg boxes (sample website).";

    /** Texto antiguo en español → texto nuevo en inglés. */
    static final Map<String, String> TRANSLATIONS = Map.ofEntries(
            Map.entry("Café de especialidad tostado cada semana en el centro de Madrid. Ven a probar nuestro flat white y los brunch de fin de semana.", CAFE),
            Map.entry("Bicicletas eléctricas y de montaña. Alquiler por días y taller exprés en 24 horas.", BIKES),
            Map.entry("Fotografía de producto y retratos profesionales para tu marca. Sesiones en estudio o a domicilio.", STUDIO),
            Map.entry("Cestas de fruta y verdura ecológica de temporada, directas del productor a tu casa.", GARDEN),
            Map.entry("Ejemplo: el canal oficial de YouTube", TASK_YOUTUBE_TITLE),
            Map.entry("Una promoción de ejemplo: así se ve un canal de YouTube en Créditos extra.", TASK_YOUTUBE_TEXT),
            Map.entry("Ejemplo: el perfil oficial de X", TASK_X_TITLE),
            Map.entry("Una promoción de ejemplo de un perfil de X (Twitter).", TASK_X_TEXT),
            Map.entry("Web de Huerta Viva", TASK_GARDEN_TITLE),
            Map.entry("Cestas de fruta y verdura ecológica (web de ejemplo).", TASK_GARDEN_TEXT));

    private DemoContent() {
    }
}
