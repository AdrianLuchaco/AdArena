package com.adarena.demo;

import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.adslot.domain.AdSlot;
import com.adarena.adslot.repository.AdSlotRepository;
import com.adarena.auction.repository.AuctionParticipationRepository;
import com.adarena.site.domain.SitePreview;
import com.adarena.site.service.SitePreviewJson;
import com.adarena.site.service.SitePreviewService;
import com.adarena.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * SOLO EN DESARROLLO. Si tu base de datos local ya tenía los anunciantes de ejemplo de antes:
 * <ul>
 *   <li>traduce sus textos de ejemplo al inglés (la web ahora está en inglés);</li>
 *   <li>les crea sus webs de ejemplo (o las actualiza) y pone la presentación al ganador que está
 *       en portada.</li>
 * </ul>
 * Así lo ves todo sin borrar tus datos. Solo toca textos de ejemplo exactos: nada de usuarios reales.
 */
@Component
@Profile("dev")
@ConditionalOnBooleanProperty("app.demo-data.enabled")
@Order(15)
public class DemoSitesBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSitesBackfill.class);

    private final UserRepository userRepository;
    private final AdProfileRepository profileRepository;
    private final AdSlotRepository adSlotRepository;
    private final AuctionParticipationRepository participationRepository;
    private final DemoSites demoSites;
    private final SitePreviewService sitePreviewService;
    private final SitePreviewJson json;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DemoSitesBackfill(UserRepository userRepository, AdProfileRepository profileRepository,
                             AdSlotRepository adSlotRepository, AuctionParticipationRepository participationRepository,
                             DemoSites demoSites, SitePreviewService sitePreviewService, SitePreviewJson json,
                             JdbcTemplate jdbc, Clock clock) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.adSlotRepository = adSlotRepository;
        this.participationRepository = participationRepository;
        this.demoSites = demoSites;
        this.sitePreviewService = sitePreviewService;
        this.json = json;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Instant now = clock.instant();
        int translated = translate("ad_profiles", "description", 300)
                + translate("auction_participations", "ad_description", 300)
                + translate("social_tasks", "title", 80)
                + translate("social_tasks", "description", 200);
        if (translated > 0) {
            log.info("Demo data translated to English ({} rows)", translated);
        }
        DemoSites.SITES.keySet().forEach(email -> userRepository.findByEmail(email)
                .flatMap(user -> profileRepository.findByUserId(user.getId()))
                .ifPresent(profile -> demoSites.ensure(email, profile, now)));

        for (AdSlot slot : adSlotRepository.findLive(now, Limit.of(1))) {
            participationRepository.findById(slot.getParticipationId())
                    .filter(participation -> participation.getAdSnapshot() != null)
                    .ifPresent(participation -> {
                        String url = participation.getAdSnapshot().websiteUrl();
                        sitePreviewService.find(url)
                                .filter(preview -> preview.getSource() == SitePreview.Source.DEMO)
                                .map(preview -> json.writeShowcase(sitePreviewService.toShowcase(url, preview)))
                                .ifPresent(showcase -> {
                                    // Ganador de ejemplo: su presentación, siempre con los textos de ejemplo actuales
                                    jdbc.update("UPDATE ad_slots SET showcase = ?::jsonb WHERE id = ?", showcase, slot.getId());
                                    log.info("Demo showcase set on the live winner ({})", url);
                                });
                    });
        }
    }

    /**
     * Cambia los textos antiguos (en español) de una columna por su traducción. Solo prueba las
     * traducciones que caben en la columna: PostgreSQL rechaza un texto más largo que el campo aunque
     * no coincida con ninguna fila (así falló con los títulos de 80 caracteres).
     */
    private int translate(String table, String column, int maxLength) {
        int rows = 0;
        for (var entry : DemoContent.TRANSLATIONS.entrySet()) {
            if (entry.getKey().length() > maxLength || entry.getValue().length() > maxLength) continue;
            rows += jdbc.update("UPDATE " + table + " SET " + column + " = ? WHERE " + column + " = ?",
                    entry.getValue(), entry.getKey());
        }
        return rows;
    }
}
