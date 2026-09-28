package com.adarena.demo;

import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.adslot.repository.AdSlotRepository;
import com.adarena.auction.repository.AuctionParticipationRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.auction.repository.BidRepository;
import com.adarena.common.config.AppProperties;
import com.adarena.image.service.ImageService;
import com.adarena.site.repository.SitePreviewRepository;
import com.adarena.site.service.SitePreviewJson;
import com.adarena.site.service.SitePreviewService;
import com.adarena.settings.repository.AppSettingsRepository;
import com.adarena.support.ApiTestSupport;
import com.adarena.support.IntegrationTest;
import com.adarena.user.repository.UserRepository;
import com.adarena.wallet.repository.LedgerAccountRepository;
import com.adarena.wallet.repository.LedgerTransactionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Los datos de demostración deben ser un escenario COHERENTE: la portada los muestra y el
 * ledger cuadra al céntimo, igual que con datos reales.
 */
@IntegrationTest
@Transactional
class DemoDataSeederTest extends ApiTestSupport {

    @Autowired AuctionRepository auctionRepository;
    @Autowired AuctionParticipationRepository participationRepository;
    @Autowired BidRepository bidRepository;
    @Autowired AdSlotRepository adSlotRepository;
    @Autowired AdProfileRepository adProfileRepository;
    @Autowired UserRepository userRepository;
    @Autowired LedgerAccountRepository accountRepository;
    @Autowired LedgerTransactionRepository transactionRepository;
    @Autowired AppSettingsRepository settingsRepository;
    @Autowired ImageService imageService;
    @Autowired SitePreviewRepository sitePreviewRepository;
    @Autowired SitePreviewService sitePreviewService;
    @Autowired SitePreviewJson sitePreviewJson;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AppProperties properties;
    @Autowired Clock clock;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsAConsistentScenario() throws Exception {
        DemoSites demoSites = new DemoSites(sitePreviewRepository, imageService, sitePreviewJson);
        DemoDataSeeder seeder = new DemoDataSeeder(auctionRepository, participationRepository, bidRepository,
                adSlotRepository, adProfileRepository, userRepository, accountRepository, transactionRepository,
                settingsRepository, imageService, demoSites, sitePreviewService, sitePreviewJson, passwordEncoder,
                properties, clock);

        seeder.run(null);
        em.flush();
        // Comprueba YA el trigger diferido que exige que cada transacción del ledger cuadre
        em.createNativeQuery("SET CONSTRAINTS ALL IMMEDIATE").executeUpdate();

        mockMvc.perform(get("/api/public/home").with(randomIp()))
                .andExpect(jsonPath("$.state").value("AD"))
                .andExpect(jsonPath("$.currentAd.companyName").value("Café Aurora"))
                // Su presentación animada (con la web de ejemplo: logo, fotos y frases)
                .andExpect(jsonPath("$.currentAd.showcase.title").value("Specialty coffee, roasted in Madrid"))
                .andExpect(jsonPath("$.currentAd.showcase.highlights.length()").value(4))
                .andExpect(jsonPath("$.currentAd.showcase.galleryUrls.length()").value(2))
                .andExpect(jsonPath("$.currentAd.showcase.themeColor").value("#f59e0b"))
                .andExpect(jsonPath("$.round.ranking[0].companyName").value("Bicis Norte"))
                .andExpect(jsonPath("$.round.ranking[0].totalPoints").value(4500)) // 2.000 puntos arrastrados + 2.500 puntos
                .andExpect(jsonPath("$.round.ranking[1].totalPoints").value(3200))
                .andExpect(jsonPath("$.round.ranking[2].totalPoints").value(1800));

        assertThat(count("SELECT count(*) FROM ledger_account_mismatches")).isZero();
        assertThat(count("SELECT COALESCE(SUM(balance_points), 0) FROM ledger_accounts")).isZero();
        // Ingresos: 6.000 puntos del ganador + 2.000 puntos perdidos por Bicis Norte
        assertThat(count("SELECT balance_points FROM ledger_accounts WHERE type = 'POINTS_SPENT'")).isEqualTo(8000);
        // Reservado = suma de las pujas vivas de hoy (45 + 32 + 18)
        assertThat(count("SELECT SUM(balance_points) FROM ledger_accounts WHERE type = 'USER_RESERVED'")).isEqualTo(9500);

        // Si ya hay subastas, no vuelve a crear nada
        long users = userRepository.count();
        seeder.run(null);
        assertThat(userRepository.count()).isEqualTo(users);
    }

    /**
     * Las bases de datos locales anteriores tienen los datos de ejemplo en español: al arrancar se
     * traducen. Antes fallaba al arrancar (y no arrancaba el backend) porque intentaba poner una
     * descripción larga en el título de una promoción, que admite como mucho 80 caracteres.
     */
    @Test
    void translatesOldSpanishDemoDataWithoutBreakingOnLongTexts() {
        DemoSites demoSites = new DemoSites(sitePreviewRepository, imageService, sitePreviewJson);
        new DemoDataSeeder(auctionRepository, participationRepository, bidRepository, adSlotRepository,
                adProfileRepository, userRepository, accountRepository, transactionRepository, settingsRepository,
                imageService, demoSites, sitePreviewService, sitePreviewJson, passwordEncoder, properties, clock).run(null);
        em.flush();

        String spanishTitle = "Web de Huerta Viva";
        String spanishText = "Cestas de fruta y verdura ecológica (web de ejemplo).";
        jdbc.update("""
                INSERT INTO social_tasks (id, owner_id, platform, title, description, url, status, reward_points)
                SELECT gen_random_uuid(), id, 'WEB', ?, ?, 'https://www.example.com/huerta-viva', 'ACTIVE', 20
                FROM users WHERE email = 'demo-huerta@adarena.local'""", spanishTitle, spanishText);
        jdbc.update("UPDATE ad_profiles SET description = ? WHERE description = ?",
                "Bicicletas eléctricas y de montaña. Alquiler por días y taller exprés en 24 horas.", DemoContent.BIKES);

        new DemoSitesBackfill(userRepository, adProfileRepository, adSlotRepository, participationRepository, demoSites,
                sitePreviewService, sitePreviewJson, jdbc, clock).run(null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM social_tasks WHERE title = ? AND description = ?", Long.class,
                DemoContent.TASK_GARDEN_TITLE, DemoContent.TASK_GARDEN_TEXT)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_profiles WHERE description = ?", Long.class,
                DemoContent.BIKES)).isEqualTo(1);
    }

    private long count(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }
}
