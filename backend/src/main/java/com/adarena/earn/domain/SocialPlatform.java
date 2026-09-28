package com.adarena.earn.domain;

import java.util.Locale;

/** Dónde apunta una promoción. Se deduce del enlace, no lo elige el usuario. */
public enum SocialPlatform {
    YOUTUBE("YouTube"),
    X("X (Twitter)"),
    INSTAGRAM("Instagram"),
    TIKTOK("TikTok"),
    TWITCH("Twitch"),
    LINKEDIN("LinkedIn"),
    FACEBOOK("Facebook"),
    GITHUB("GitHub"),
    WEB("Web");

    private final String label;

    SocialPlatform(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** "https://www.youtube.com/@canal" → YOUTUBE. Cualquier otro dominio → WEB. */
    public static SocialPlatform fromUrl(String url) {
        String host = url.replaceFirst("^https://", "").split("[/:?#]", 2)[0].toLowerCase(Locale.ROOT);
        if (matches(host, "youtube.com") || matches(host, "youtu.be")) return YOUTUBE;
        if (matches(host, "x.com") || matches(host, "twitter.com")) return X;
        if (matches(host, "instagram.com")) return INSTAGRAM;
        if (matches(host, "tiktok.com")) return TIKTOK;
        if (matches(host, "twitch.tv")) return TWITCH;
        if (matches(host, "linkedin.com")) return LINKEDIN;
        if (matches(host, "facebook.com") || matches(host, "fb.com")) return FACEBOOK;
        if (matches(host, "github.com")) return GITHUB;
        return WEB;
    }

    private static boolean matches(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }
}
