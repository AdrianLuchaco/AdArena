package com.adarena.notification.service;

import com.adarena.common.text.Points;
import com.adarena.notification.domain.NotificationType;

/** Los textos de todos los avisos, en un solo sitio. */
public final class Notices {

    private Notices() {
    }

    /** Regla 4: "Otra persona te ha superado, vuelve a pujar" (con enlace directo). */
    public static Notice outbid() {
        return new Notice(NotificationType.OUTBID, "You've been outbid in the Race",
                "Someone just outbid you. Bid again to take your spot back before the Race closes.",
                "/race", true, "Bid again");
    }

    public static Notice won(long totalPoints) {
        return new Notice(NotificationType.AUCTION_WON, "You won the Race!",
                "Your project finished first with " + Points.format(totalPoints) + ". We'll review your ad and it will "
                        + "take over the homepage as soon as we approve it. If we can't publish it, you get all your points back.",
                "/account", true, "See my ad");
    }

    public static Notice lost(long carriedPoints) {
        String body = carriedPoints > 0
                ? "Another project won this time. You keep " + Points.format(carriedPoints)
                  + " as your starting bid in today's Race, automatically."
                : "Another project won this time. You get another shot tomorrow!";
        return new Notice(NotificationType.AUCTION_LOST, "The Race has closed", body, "/race", false, null);
    }

    public static Notice promoted(long totalPoints) {
        return new Notice(NotificationType.CANDIDATE_PROMOTED, "You're the winner now!",
                "The ad that finished first couldn't be published, so your project becomes the winner with "
                        + Points.format(totalPoints) + ". We'll review your ad and it will take over the homepage as soon as we approve it.",
                "/account", true, "See my ad");
    }

    public static Notice approved(String untilText, long winnerBonusPoints) {
        String bonus = winnerBonusPoints > 0
                ? " And here are " + Points.format(winnerBonusPoints) + " on us, so you can bid again."
                : "";
        return new Notice(NotificationType.AD_APPROVED, "Your ad is on the homepage",
                "Congratulations! Everyone will see your ad on the LaunchCrown homepage until " + untilText + "." + bonus,
                "/", true, "See it live");
    }

    public static Notice rejected(String reason, long refundedPoints) {
        return new Notice(NotificationType.AD_REJECTED, "Your ad wasn't published",
                "We couldn't publish your ad. Reason: " + sentence(reason) + " We've refunded "
                        + Points.format(refundedPoints) + ".",
                "/account/points", true, "See my points");
    }

    public static Notice expired(long refundedPoints) {
        return new Notice(NotificationType.WINNER_REFUNDED, "Your points are back",
                "We couldn't review your ad in time, so we've refunded 100% ("
                        + Points.format(refundedPoints) + ").",
                "/account/points", true, "See my points");
    }

    public static Notice taskHidden(String title, String reason) {
        return new Notice(NotificationType.TASK_HIDDEN, "We've hidden one of your promotions",
                "Your promotion \u201c" + title + "\u201d is no longer shown in Bonus links. Reason: " + sentence(reason),
                "/promote", true, "See my promotions");
    }

    /** "no es legible" → "no es legible." (los motivos los escribe el admin, a veces sin punto final) */
    private static String sentence(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.isEmpty() || ".!?…".indexOf(trimmed.charAt(trimmed.length() - 1)) >= 0 ? trimmed : trimmed + ".";
    }
}
