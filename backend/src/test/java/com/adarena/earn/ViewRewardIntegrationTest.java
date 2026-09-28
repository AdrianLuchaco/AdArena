package com.adarena.earn;

import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.auction.domain.Auction;
import com.adarena.auction.repository.AuctionParticipationRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.auction.service.BidService;
import com.adarena.common.error.ApiException;
import com.adarena.earn.domain.SocialTask;
import com.adarena.earn.dto.EarnDtos;
import com.adarena.earn.repository.SocialTaskRepository;
import com.adarena.earn.service.SocialTaskService;
import com.adarena.earn.service.ViewRewardService;
import com.adarena.image.service.ImageService;
import com.adarena.site.dto.SiteDtos.ViewMode;
import com.adarena.support.ArenaFixtures;
import com.adarena.support.IsolatedArenaTest;
import com.adarena.user.repository.UserRepository;
import com.adarena.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Intercambio de visitas: 10 puntos cada 10 s mirando la web del proyecto, bonus a los 60 s (100 en
 * total), 100 al día por proyecto y, sobre todo, las defensas del SERVIDOR contra las trampas (el
 * navegador se puede manipular). Para no esperar de verdad, los tests "atrasan el reloj" en la base de datos.
 */
@IsolatedArenaTest
class ViewRewardIntegrationTest {

    @Autowired ViewRewardService viewRewardService;
    @Autowired SocialTaskService socialTaskService;
    @Autowired SocialTaskRepository socialTaskRepository;
    @Autowired BidService bidService;
    @Autowired WalletService walletService;
    @Autowired AuctionRepository auctionRepository;
    @Autowired AuctionParticipationRepository participationRepository;
    @Autowired UserRepository userRepository;
    @Autowired AdProfileRepository adProfileRepository;
    @Autowired ImageService imageService;
    @Autowired JdbcTemplate jdbc;

    ArenaFixtures fixtures;
    Auction round;
    UUID owner;
    UUID project;
    UUID viewer;

    @BeforeEach
    void setUp() {
        fixtures = new ArenaFixtures(userRepository, adProfileRepository, imageService, walletService,
                auctionRepository, jdbc);
        round = fixtures.openRound(Duration.ofHours(3));
        owner = fixtures.bidder("Proyecto visitado", 1_000);
        bidService.placeBid(owner, 100, "k-" + UUID.randomUUID());
        project = participationRepository.findByAuctionIdAndUserId(round.getId(), owner).orElseThrow().getId();
        viewer = fixtures.bidder("Visitante", 0);
    }

    /** Como si hubieran pasado {@code seconds} segundos desde la última actividad de este usuario. */
    private void waitSeconds(UUID user, int seconds) {
        jdbc.update("""
                UPDATE project_views
                SET session_started_at = session_started_at - make_interval(secs => ?),
                    last_tick_at = last_tick_at - make_interval(secs => ?)
                WHERE viewer_id = ?
                """, seconds, seconds, user);
        jdbc.update("""
                UPDATE social_task_completions
                SET started_at = started_at - make_interval(secs => ?),
                    completed_at = completed_at - make_interval(secs => ?)
                WHERE user_id = ?
                """, seconds, seconds, user);
    }

    private long available(UUID user) {
        return walletService.balance(user).availablePoints();
    }

    @Test
    void tenPointsEveryTenSecondsAndABonusAtSixtySecondsUpToOneHundred() {
        viewRewardService.start(viewer, project);
        int total = 0;
        for (int tick = 1; tick <= 6; tick++) {
            waitSeconds(viewer, 10);
            EarnDtos.TickResult result = viewRewardService.tick(viewer, project, 1);
            total += result.pointsAwarded();
            if (tick < 6) {
                assertThat(result.pointsAwarded()).isEqualTo(10);
                assertThat(result.bonusAwarded()).isFalse();
            } else {
                assertThat(result.pointsAwarded()).isEqualTo(50); // 10 + 40 de bonus a los 60 s
                assertThat(result.bonusAwarded()).isTrue();
                assertThat(result.capReached()).isTrue();
            }
        }
        assertThat(total).isEqualTo(100);
        assertThat(available(viewer)).isEqualTo(100);

        // Límite diario: ese proyecto ya no da más puntos hoy
        waitSeconds(viewer, 10);
        EarnDtos.TickResult extra = viewRewardService.tick(viewer, project, 1);
        assertThat(extra.pointsAwarded()).isZero();
        assertThat(extra.capReached()).isTrue();
        assertThat(available(viewer)).isEqualTo(100);
        assertThat(viewRewardService.start(viewer, project).reason()).isEqualTo("CAP_REACHED");
        assertThat(fixtures.ledgerMismatches()).isZero();
    }

    @Test
    void aTickBeforeTenSecondsIsRejected() {
        viewRewardService.start(viewer, project);

        assertThatThrownBy(() -> viewRewardService.tick(viewer, project, 1))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getCode()).isEqualTo("TICK_TOO_SOON");
                    assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                });
        assertThat(available(viewer)).isZero();
    }

    @Test
    void manyTabsNeverEarnFasterThanTheClock() {
        UUID secondOwner = fixtures.bidder("Otro proyecto", 1_000);
        bidService.placeBid(secondOwner, 100, "k-" + UUID.randomUUID());
        UUID secondProject = participationRepository.findByAuctionIdAndUserId(round.getId(), secondOwner).orElseThrow().getId();
        viewRewardService.start(viewer, project);
        viewRewardService.start(viewer, secondProject);
        waitSeconds(viewer, 10);

        viewRewardService.tick(viewer, project, 1);
        // La otra pestaña intenta cobrar a la vez: el último tick (de cualquier proyecto) fue hace nada
        assertThatThrownBy(() -> viewRewardService.tick(viewer, secondProject, 1))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("TICK_TOO_SOON");
        assertThat(available(viewer)).isEqualTo(10);
    }

    @Test
    void comingBackFromAnotherWindowCountsSeveralTicksButNeverMoreThanTheClock() {
        // La web estuvo 35 s abierta en su propia ventana: al volver, la página pide los 6 tramos
        // que cree que le tocan, pero el servidor solo da los que caben en el tiempo real (3)
        viewRewardService.start(viewer, project);
        waitSeconds(viewer, 35);
        EarnDtos.TickResult first = viewRewardService.tick(viewer, project, 6);
        assertThat(first.ticksAwarded()).isEqualTo(3);
        assertThat(first.pointsAwarded()).isEqualTo(30);

        // Pedirlo otra vez al instante no da nada
        assertThatThrownBy(() -> viewRewardService.tick(viewer, project, 6))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("TICK_TOO_SOON");

        // 40 s más: 3 tramos (hasta los 60 s) con su bonus, y se completa el máximo de hoy
        waitSeconds(viewer, 40);
        EarnDtos.TickResult second = viewRewardService.tick(viewer, project, 6);
        assertThat(second.ticksAwarded()).isEqualTo(3);
        assertThat(second.bonusAwarded()).isTrue();
        assertThat(second.pointsAwarded()).isEqualTo(70); // 30 + 40 de bonus
        assertThat(second.capReached()).isTrue();
        assertThat(available(viewer)).isEqualTo(100);
        assertThat(fixtures.ledgerMismatches()).isZero();
    }

    @Test
    void projectsAndExtraCreditsShareTheSameClock() {
        // No se pueden mirar dos cosas a la vez: si acabas de cobrar una tarea, el proyecto espera
        SocialTask task = socialTaskRepository.save(new SocialTask(owner, "Mi canal", null,
                "https://www.youtube.com/@reloj-compartido", 20));
        viewRewardService.start(viewer, project);
        socialTaskService.start(viewer, task.getId());
        waitSeconds(viewer, 11);

        assertThat(socialTaskService.claim(viewer, task.getId()).pointsAwarded()).isEqualTo(20);
        assertThatThrownBy(() -> viewRewardService.tick(viewer, project, 1))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("TICK_TOO_SOON");

        waitSeconds(viewer, 10);
        assertThat(viewRewardService.tick(viewer, project, 1).pointsAwarded()).isEqualTo(10);
        assertThat(available(viewer)).isEqualTo(30);
    }

    @Test
    void aTickNeedsTheProjectPageToBeOpenedFirst() {
        assertThatThrownBy(() -> viewRewardService.tick(viewer, project, 1))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("VIEW_NOT_STARTED");
    }

    @Test
    void yourOwnProjectNeverGivesPoints() {
        EarnDtos.ViewStatus status = viewRewardService.start(owner, project);

        assertThat(status.canEarn()).isFalse();
        assertThat(status.reason()).isEqualTo("OWN_PROJECT");
        assertThatThrownBy(() -> viewRewardService.tick(owner, project, 1))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("OWN_PROJECT");
    }

    @Test
    void onlyProjectsCompetingTodayGivePoints() {
        fixtures.closeOpenRounds();

        assertThatThrownBy(() -> viewRewardService.start(viewer, project))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("PROJECT_NOT_IN_ARENA");
        // Su página pública sigue existiendo, pero ya no está en la Arena
        assertThat(viewRewardService.publicProject(project).inArena()).isFalse();
    }

    @Test
    void theOverviewShowsWhatYouAlreadyEarnedWithEachProject() {
        viewRewardService.start(viewer, project);
        waitSeconds(viewer, 10);
        viewRewardService.tick(viewer, project, 1);

        EarnDtos.EarnOverview overview = viewRewardService.overview(viewer);

        EarnDtos.EarnProject listed = overview.projects().stream()
                .filter(p -> p.id().equals(project)).findFirst().orElseThrow();
        assertThat(listed.pointsEarnedToday()).isEqualTo(10);
        assertThat(listed.dailyCap()).isEqualTo(100);
        assertThat(listed.own()).isFalse();
        assertThat(overview.earnedTodayFromViews()).isEqualTo(10);
        assertThat(overview.rules().tickSeconds()).isEqualTo(10);
        assertThat(overview.rules().winnerBonus()).isEqualTo(500);
        // Su web todavía no se ha leído (en los tests nunca se sale a internet): se abrirá en una ventana
        assertThat(listed.site().mode()).isEqualTo(ViewMode.WINDOW);
        assertThat(listed.site().openUrl()).startsWith("https://");
    }

    @Test
    void theDatabaseItselfForbidsViewingYourOwnProject() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO project_views (id, viewer_id, owner_id, view_date, session_started_at)
                VALUES (gen_random_uuid(), ?, ?, current_date, now())
                """, owner, owner))
                .hasMessageContaining("ck_project_views_not_self");
    }
}
