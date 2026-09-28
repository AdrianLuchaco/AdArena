package com.adarena.common.text;

import java.text.NumberFormat;
import java.util.Locale;

/** Formato de puntos para los mensajes al usuario: 1250 → "1,250 points", 1 → "1 point". */
public final class Points {

    private static final Locale ENGLISH = Locale.forLanguageTag("en-GB");

    private Points() {
    }

    public static String format(long points) {
        String number = NumberFormat.getIntegerInstance(ENGLISH).format(points);
        return number + (points == 1 ? " point" : " points");
    }
}
