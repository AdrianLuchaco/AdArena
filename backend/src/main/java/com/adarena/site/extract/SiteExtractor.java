package com.adarena.site.extract;

import com.adarena.common.text.TextSanitizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Saca lo importante del HTML de una web: nombre, titular, descripción, color de marca, logo,
 * foto principal, otras fotos y frases destacadas (sus títulos de sección). Con eso se monta la
 * presentación animada del ganador y la ficha de cada web en el visor.
 * <p>
 * Todo el texto se limpia (una línea, sin caracteres de control) y se recorta. React lo muestra
 * siempre como texto: nunca se inserta HTML de la web en LaunchCrown.
 */
public final class SiteExtractor {

    public static final int MAX_TITLE = 200;
    public static final int MAX_DESCRIPTION = 500;
    public static final int MAX_SITE_NAME = 120;
    public static final int MAX_HIGHLIGHT = 110;
    public static final int MAX_HIGHLIGHTS = 5;
    public static final int MAX_IMAGE_CANDIDATES = 8;

    private static final Pattern CHARSET = Pattern.compile("charset=\"?([A-Za-z0-9_.:-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX = Pattern.compile("^#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");
    private static final Pattern SIZES = Pattern.compile("(\\d+)x(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOISE = Pattern.compile(
            "cookie|consent|gdpr|banner|modal|popup|newsletter|navbar|breadcrumb|footer|sidebar|share|social"
                    + "|visually-hidden|sr-only|screen-reader",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_A_PHOTO = Pattern.compile(
            "(pixel|spacer|tracking|blank|placeholder|sprite|avatar|icon|emoji|badge)|\\.svg(\\?|$)",
            Pattern.CASE_INSENSITIVE);

    private SiteExtractor() {
    }

    /**
     * @param iconSources  candidatos a logo, del mejor al peor
     * @param heroSource   la foto principal (la que la web elige para compartirse en redes)
     * @param imageSources otras fotos grandes de la página
     */
    public record ExtractedSite(String siteName, String title, String description, String themeColor,
                                List<String> iconSources, String heroSource, List<String> imageSources,
                                List<String> highlights) {
    }

    public static ExtractedSite extract(byte[] html, String contentType, String baseUrl) {
        Document doc;
        try {
            doc = Jsoup.parse(new ByteArrayInputStream(html), charsetOf(contentType), baseUrl);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String title = clean(firstNonBlank(meta(doc, "og:title"), meta(doc, "twitter:title"), doc.title(),
                text(doc.selectFirst("h1"))), MAX_TITLE);
        String description = clean(firstNonBlank(meta(doc, "og:description"), meta(doc, "description"),
                meta(doc, "twitter:description"), firstParagraph(doc)), MAX_DESCRIPTION);
        String siteName = clean(firstNonBlank(meta(doc, "og:site_name"), meta(doc, "application-name"),
                siteNameFromTitle(doc.title())), MAX_SITE_NAME);

        String hero = https(firstNonBlank(absMeta(doc, "og:image:secure_url"), absMeta(doc, "og:image"),
                absMeta(doc, "twitter:image"), absMeta(doc, "twitter:image:src")));

        return new ExtractedSite(siteName, title, description, themeColor(meta(doc, "theme-color")),
                iconCandidates(doc, baseUrl), hero, imageCandidates(doc, hero),
                highlights(doc, title, siteName));
    }

    // ------------------------------------------------------------------ piezas

    private static String charsetOf(String contentType) {
        if (contentType == null) {
            return null;
        }
        Matcher matcher = CHARSET.matcher(contentType);
        if (matcher.find() && Charset.isSupported(matcher.group(1))) {
            return matcher.group(1);
        }
        return null;  // Jsoup lo detecta por la etiqueta <meta charset> (o usa UTF-8)
    }

    /** &lt;meta property="og:title"&gt; o &lt;meta name="description"&gt;. */
    private static String meta(Document doc, String key) {
        Element element = doc.selectFirst("meta[property=" + key + "], meta[name=" + key + "]");
        return element == null ? null : element.attr("content");
    }

    private static String absMeta(Document doc, String key) {
        Element element = doc.selectFirst("meta[property=" + key + "], meta[name=" + key + "]");
        if (element == null || element.attr("content").isBlank()) {
            return null;
        }
        return element.absUrl("content");
    }

    private static String text(Element element) {
        return element == null ? null : element.text();
    }

    /** "Café Aurora | Café de especialidad" → "Café Aurora". */
    private static String siteNameFromTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String[] parts = title.split("\\s[|\\-–—·:]\\s");
        return parts[0].length() <= 40 ? parts[0] : null;
    }

    private static String firstParagraph(Document doc) {
        for (Element paragraph : content(doc).select("p")) {
            String text = paragraph.text();
            if (text.length() >= 60 && !isNoise(paragraph)) {
                return text;
            }
        }
        return null;
    }

    /** "#1F7A5A" o "#abc" → "#1f7a5a". Colores casi blancos o casi negros no dicen nada de la marca. */
    static String themeColor(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher matcher = HEX.matcher(raw.trim());
        if (!matcher.matches()) {
            return null;
        }
        String hex = matcher.group(1).toLowerCase(Locale.ROOT);
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        int rgb = Integer.parseInt(hex, 16);
        double luminance = (0.2126 * (rgb >> 16 & 0xff) + 0.7152 * (rgb >> 8 & 0xff) + 0.0722 * (rgb & 0xff)) / 255;
        if (luminance > 0.92 || luminance < 0.04) {
            return null;
        }
        return "#" + hex;
    }

    /** Logos: apple-touch-icon (suelen ser 180×180) antes que los "favicon" pequeños. */
    private static List<String> iconCandidates(Document doc, String baseUrl) {
        record Icon(String url, int size, boolean apple) {
        }
        List<Icon> icons = new ArrayList<>();
        for (Element link : doc.select("link[rel~=(?i)icon]")) {
            String url = https(link.absUrl("href"));
            if (url == null || url.toLowerCase(Locale.ROOT).contains(".svg")) {
                continue;
            }
            int size = 0;
            Matcher sizes = SIZES.matcher(link.attr("sizes"));
            if (sizes.find()) {
                size = Integer.parseInt(sizes.group(1));
            }
            boolean apple = link.attr("rel").toLowerCase(Locale.ROOT).contains("apple-touch-icon");
            icons.add(new Icon(url, size, apple));
        }
        icons.sort(Comparator.comparing(Icon::apple).reversed().thenComparing(Icon::size, Comparator.reverseOrder()));
        Set<String> result = new LinkedHashSet<>();
        icons.forEach(icon -> result.add(icon.url()));
        // Muchas webs lo tienen aunque no lo declaren
        try {
            result.add(URI.create(baseUrl).resolve("/apple-touch-icon.png").toString());
        } catch (IllegalArgumentException ignored) {
            // dirección rara: sin candidato extra
        }
        return result.stream().limit(4).toList();
    }

    /** Fotos grandes del contenido (no iconos, ni píxeles de seguimiento, ni la foto principal otra vez). */
    private static List<String> imageCandidates(Document doc, String hero) {
        Set<String> result = new LinkedHashSet<>();
        for (Element image : content(doc).select("img")) {
            if (result.size() >= MAX_IMAGE_CANDIDATES) {
                break;
            }
            if (isNoise(image) || tooSmall(image)) {
                continue;
            }
            String url = https(bestSource(image));
            if (url == null || url.equals(hero) || NOT_A_PHOTO.matcher(url).find()) {
                continue;
            }
            result.add(url);
        }
        return List.copyOf(result);
    }

    /** src, las variantes "lazy" (data-src) o la más grande de srcset. */
    private static String bestSource(Element image) {
        String srcset = image.hasAttr("srcset") ? image.attr("srcset") : image.attr("data-srcset");
        if (!srcset.isBlank()) {
            String best = null;
            int bestWidth = -1;
            for (String candidate : srcset.split(",")) {
                String[] parts = candidate.trim().split("\\s+");
                if (parts.length == 0 || parts[0].isBlank()) {
                    continue;
                }
                int width = parts.length > 1 && parts[1].endsWith("w") ? parseInt(parts[1].replace("w", "")) : 0;
                if (width > bestWidth && width <= 2400) {
                    best = parts[0];
                    bestWidth = width;
                }
            }
            if (best != null) {
                return resolve(image.baseUri(), best);
            }
        }
        for (String attribute : List.of("src", "data-src", "data-lazy-src", "data-original")) {
            String url = image.absUrl(attribute);
            if (!url.isBlank() && !url.startsWith("data:")) {
                return url;
            }
        }
        return null;
    }

    private static boolean tooSmall(Element image) {
        int width = parseInt(image.attr("width"));
        int height = parseInt(image.attr("height"));
        return (width > 0 && width < 200) || (height > 0 && height < 150);
    }

    /** Titulares de sección: "Tostamos cada semana", "Brunch los domingos"… */
    private static List<String> highlights(Document doc, String title, String siteName) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        String normalizedTitle = title == null ? "" : title.toLowerCase(Locale.ROOT);
        String normalizedName = siteName == null ? "" : siteName.toLowerCase(Locale.ROOT);
        for (Element heading : content(doc).select("h1, h2, h3")) {
            if (result.size() >= MAX_HIGHLIGHTS) {
                break;
            }
            if (isNoise(heading)) {
                continue;
            }
            String text = clean(heading.text(), MAX_HIGHLIGHT);
            if (text == null || text.length() < 4 || text.length() > MAX_HIGHLIGHT - 1) {
                continue;
            }
            String key = text.toLowerCase(Locale.ROOT);
            if (key.equals(normalizedTitle) || key.equals(normalizedName) || !seen.add(key)) {
                continue;
            }
            result.add(text);
        }
        return result;
    }

    /**
     * El contenido principal si la web lo marca (&lt;main&gt;, &lt;article&gt;) y de verdad lo contiene
     * (al menos la mitad del texto de la página: algunas webs solo meten la cabecera en &lt;main&gt;);
     * si no, todo el body.
     */
    private static Element content(Document doc) {
        Element main = doc.selectFirst("main, [role=main], article");
        Element body = doc.body();
        if (main != null && body != null && main.text().length() * 2 >= body.text().length()) {
            return main;
        }
        return body != null ? body : doc;
    }

    /** Menús, pies de página, avisos de cookies…: no son el contenido de la web. */
    private static boolean isNoise(Element element) {
        for (Element current = element; current != null; current = current.parent()) {
            String tag = current.tagName();
            if (tag.equals("nav") || tag.equals("footer") || tag.equals("aside") || tag.equals("form")
                    || tag.equals("dialog") || tag.equals("blockquote")) {
                return true;
            }
            // La cabecera de la web (logo y menú), pero no la de una sección del contenido
            if (tag.equals("header") && current.parent() != null && current.parent().tagName().equals("body")) {
                return true;
            }
            String classes = current.className() + " " + current.id();
            if (!classes.isBlank() && NOISE.matcher(classes).find()) {
                return true;
            }
        }
        return false;
    }

    private static String https(String url) {
        return url != null && url.startsWith("https://") && url.length() <= 2048 ? url : null;
    }

    private static String resolve(String base, String relative) {
        try {
            return URI.create(base).resolve(relative.trim()).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String clean(String value, int max) {
        String text = TextSanitizer.singleLine(value);
        if (text == null || text.isEmpty()) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max - 1).trim() + "…";
    }
}
