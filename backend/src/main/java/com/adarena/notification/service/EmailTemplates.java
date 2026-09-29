package com.adarena.notification.service;

import org.springframework.web.util.HtmlUtils;

/**
 * Plantillas de los emails: versión HTML (bonita) y versión de texto plano (para los lectores
 * de correo que no muestran HTML y para los filtros anti-spam, que la valoran).
 * <p>
 * SEGURIDAD: todo lo que viene del usuario o del admin (nombres, motivos…) se "escapa" con
 * {@link HtmlUtils#htmlEscape}: un nombre como {@code <script>} se muestra como texto y nunca
 * se interpreta como HTML.
 */
public final class EmailTemplates {

    private static final String BRAND = "#2437ff";
    private static final String INK = "#0c0f1f";

    private EmailTemplates() {
    }

    public record EmailContent(String subject, String html, String text) {
    }

    /** Email de un aviso: saludo, título, texto y botón que lleva a la web. */
    public static EmailContent notice(Notice notice, String recipientName, String webUrl) {
        String url = webUrl + notice.link();
        String button = notice.emailButton() == null ? "Open LaunchCrown" : notice.emailButton();
        return new EmailContent(notice.title(),
                layout(recipientName, notice.title(), notice.body(), button, url, null),
                plain(recipientName, notice.title(), notice.body(), button, url, null));
    }

    /** "He olvidado mi contraseña". */
    public static EmailContent passwordReset(String recipientName, String resetUrl, long validMinutes) {
        String title = "Reset your password";
        String body = "We received a request to change the password of your LaunchCrown account. "
                + "Press the button to choose a new one. The link expires in " + validMinutes + " minutes and "
                + "works only once.";
        String footnote = "If this wasn't you, ignore this email: your password won't change.";
        return new EmailContent("Reset your LaunchCrown password",
                layout(recipientName, title, body, "Choose a new password", resetUrl, footnote),
                plain(recipientName, title, body, "Choose a new password", resetUrl, footnote));
    }

    private static String layout(String name, String title, String body, String button, String url, String footnote) {
        String safeUrl = HtmlUtils.htmlEscape(url);
        return """
                <!doctype html>
                <html lang="en">
                <body style="margin:0;padding:0;background:#eceef2;font-family:Helvetica,Arial,sans-serif;color:%s">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#eceef2;padding:32px 12px">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:520px;background:#ffffff;border-radius:8px;padding:32px;border:2px solid #0c0f1f">
                        <tr><td>
                          <p style="margin:0 0 24px;font-size:20px;font-weight:800;letter-spacing:-0.02em">
                            <span style="display:inline-block;width:14px;height:14px;border-radius:4px;background:%s;margin-right:8px"></span>LaunchCrown
                          </p>
                          <p style="margin:0 0 8px;font-size:15px;color:#5b6070">Hi %s,</p>
                          <h1 style="margin:0 0 12px;font-size:24px;line-height:1.2">%s</h1>
                          <p style="margin:0 0 28px;font-size:16px;line-height:1.6;color:#2b2f40">%s</p>
                          <a href="%s" style="display:inline-block;background:%s;color:#ffffff;text-decoration:none;font-weight:700;padding:14px 28px;border-radius:6px">%s</a>
                          <p style="margin:28px 0 0;font-size:13px;line-height:1.5;color:#5b6070">%s</p>
                        </td></tr>
                      </table>
                      <p style="margin:20px 0 0;font-size:12px;color:#5b6070">LaunchCrown. One homepage, one winner, every day.</p>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(INK, BRAND, escape(name), escape(title), escape(body), safeUrl, BRAND, escape(button),
                footnote == null ? "If the button doesn't work, copy this address into your browser: " + safeUrl
                        : escape(footnote) + "<br>If the button doesn't work, copy this address into your browser: " + safeUrl);
    }

    private static String plain(String name, String title, String body, String button, String url, String footnote) {
        return "Hi " + name + ",\n\n" + title + "\n\n" + body + "\n\n" + button + ": " + url + "\n"
                + (footnote == null ? "" : "\n" + footnote + "\n")
                + "\n--\nAdArena. One homepage, one winner, every day.\n";
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
