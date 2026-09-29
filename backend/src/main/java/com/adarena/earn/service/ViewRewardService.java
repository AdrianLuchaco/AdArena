package com.adarena.earn.service;

import com.adarena.adprofile.domain.AdProfile;
import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.auction.domain.Auction;
import com.adarena.auction.domain.AuctionParticipation;
import com.adarena.auction.domain.AuctionStatus;
import com.adarena.auction.repository.AuctionParticipationRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.auction.repository.RankingRow;
import com.adarena.common.config.AppProperties;
import com.adarena.common.error.ApiException;
import com.adarena.earn.domain.ProjectView;
import com.adarena.earn.dto.EarnDtos;
import com.adarena.earn.repository.ProjectViewRepository;
import com.adarena.earn.repository.SocialTaskCompletionRepository;
import com.adarena.image.service.ImageService;
import com.adarena.site.dto.SiteDtos.SiteInfo;
import com.adarena.site.service.SitePreviewService;
import com.adarena.wallet.domain.LedgerTransactionType;
import com.adarena.wallet.service.WalletService;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Intercambio de visitas: los usuarios ganan puntos viendo la WEB de los proyectos de la Arena.
 * <ul>
 *   <li>La web cuenta el tiempo que pasas mirando la web del proyecto: dentro de LaunchCrown (iframe) o,
 *       si no se deja mostrar, en su propia ventana. Se pausa si te vas a otra cosa.</li>
 *   <li>Cada tramo de 10 s da 10 puntos; al llegar a 60 s, 40 de bonus (100 en total). Como mucho 100
 *       puntos al día por proyecto.</li>
 * </ul>
 * <b>La seguridad está AQUÍ, no en el navegador</b> (el navegador se puede manipular):
 * <ol>
 *   <li>Nadie gana más deprisa que el reloj: el tiempo que se paga nunca supera el tiempo real que
 *       ha pasado desde el último premio del usuario, sea de una visita o de una tarea de Créditos
 *       extra (el "reloj de atención" es uno por persona: no se pueden mirar dos cosas a la vez).
 *       Abrir diez pestañas no multiplica nada. Los premios de un usuario se procesan de uno en uno
 *       (se bloquean sus cuentas de puntos).</li>
 *   <li>El tiempo empieza a contar cuando se abre el visor del proyecto (lo apunta el servidor).</li>
 *   <li>Límite diario por proyecto, y nadie gana puntos con su propio proyecto (también lo impide la
 *       base de datos).</li>
 *   <li>Cada tick queda en el libro de movimientos con una clave única: no se puede cobrar dos veces.</li>
 * </ol>
 */
@Service
public class ViewRewardService {

    /** Margen para la latencia de red: la web espera 10 s exactos, al servidor le pueden llegar en 9,6. */
    private static final Duration NETWORK_TOLERANCE = Duration.ofMillis(400);
    private static final int MAX_LISTED = 50;

    private final AuctionRepository auctionRepository;
    private final AuctionParticipationRepository participationRepository;
    private final AdProfileRepository adProfileRepository;
    private final ProjectViewRepository viewRepository;
    private final SocialTaskCompletionRepository completionRepository;
    private final WalletService walletService;
    private final SitePreviewService sitePreviewService;
    private final AttentionClock attentionClock;
    private final RewardRules rules;
    private final Clock clock;

    public ViewRewardService(AuctionRepository auctionRepository, AuctionParticipationRepository participationRepository,
                             AdProfileRepository adProfileRepository, ProjectViewRepository viewRepository,
                             SocialTaskCompletionRepository completionRepository, WalletService walletService,
                             SitePreviewService sitePreviewService, AttentionClock attentionClock, RewardRules rules,
                             Clock clock) {
        this.auctionRepository = auctionRepository;
        this.participationRepository = participationRepository;
        this.adProfileRepository = adProfileRepository;
        this.viewRepository = viewRepository;
        this.completionRepository = completionRepository;
        this.walletService = walletService;
        this.sitePreviewService = sitePreviewService;
        this.attentionClock = attentionClock;
        this.rules = rules;
        this.clock = clock;
    }

    /** "Gana puntos": los proyectos de hoy con lo que ya has ganado con cada uno. */
    @Transactional(readOnly = true)
    public EarnDtos.EarnOverview overview(UUID userId) {
        LocalDate today = rules.today();
        int cap = rules.views().dailyCapPerProject();
        Map<UUID, ProjectView> viewedToday = viewRepository.findByViewerIdAndViewDate(userId, today).stream()
                .collect(Collectors.toMap(ProjectView::getOwnerId, Function.identity()));

        List<EarnDtos.EarnProject> projects = new ArrayList<>();
        auctionRepository.findFirstByStatus(AuctionStatus.OPEN).ifPresent(round -> {
            List<RankingRow> ranking = participationRepository.findRanking(round.getId(), Limit.of(MAX_LISTED));
            Map<UUID, UUID> owners = participationRepository
                    .findAllById(ranking.stream().map(RankingRow::participationId).toList()).stream()
                    .collect(Collectors.toMap(AuctionParticipation::getId, AuctionParticipation::getUserId));
            Map<String, UUID> sites = new HashMap<>();
            ranking.forEach(row -> sites.putIfAbsent(row.websiteUrl(), owners.get(row.participationId())));
            Map<String, SiteInfo> siteInfos = sitePreviewService.siteInfos(sites);
            for (int i = 0; i < ranking.size(); i++) {
                RankingRow row = ranking.get(i);
                UUID owner = owners.get(row.participationId());
                ProjectView view = viewedToday.get(owner);
                projects.add(new EarnDtos.EarnProject(row.participationId(), i + 1, row.companyName(), row.description(),
                        ImageService.publicUrl(row.imageId()), row.totalPoints(), userId.equals(owner),
                        view == null ? 0 : view.getPointsEarned(), cap, siteInfos.get(row.websiteUrl())));
            }
        });

        WalletService.Balance balance = walletService.balance(userId);
        return new EarnDtos.EarnOverview(balance.availablePoints(), balance.reservedPoints(),
                viewRepository.sumPointsEarned(userId, today), completionRepository.sumPointsAwarded(userId, today),
                completionRepository.countByUserIdAndTaskDateAndCompletedAtIsNotNull(userId, today),
                rules.toDto(), projects);
    }

    /** Página pública de un proyecto: su anuncio y cómo va en la Arena. */
    @Transactional(readOnly = true)
    public EarnDtos.PublicProject publicProject(UUID participationId) {
        AuctionParticipation participation = participationRepository.findById(participationId)
                .orElseThrow(ApiExceptions::projectNotFound);
        Auction round = participation.getAuction();
        boolean inArena = round.isOpen() && participation.getTotalPoints() > 0;
        AdProfile profile = adProfileRepository.findByUserId(participation.getUserId())
                .orElseThrow(ApiExceptions::projectNotFound);
        // En la Arena de hoy se muestra su anuncio actual; de días anteriores, la foto que quedó al cierre
        boolean useSnapshot = !round.isOpen() && participation.getAdSnapshot() != null;
        Integer position = inArena ? participationRepository.countAhead(round.getId(), participation.getTotalPoints(),
                participation.getLastBidSeq()) + 1 : null;
        String websiteUrl = useSnapshot ? participation.getAdSnapshot().websiteUrl() : profile.getWebsiteUrl();
        return new EarnDtos.PublicProject(participation.getId(),
                useSnapshot ? participation.getAdSnapshot().companyName() : profile.getCompanyName(),
                useSnapshot ? participation.getAdSnapshot().description() : profile.getDescription(),
                websiteUrl,
                ImageService.publicUrl(useSnapshot ? participation.getAdSnapshot().imageId() : profile.getImageId()),
                participation.getTotalPoints(), participation.getCarriedInPoints(), position, inArena,
                round.isOpen() ? round.getEndsAt() : null,
                sitePreviewService.siteInfo(websiteUrl, participation.getUserId()));
    }

    /** Al abrir la página de un proyecto: empieza la visita (el reloj del servidor empieza a contar). */
    @Transactional
    public EarnDtos.ViewStatus start(UUID viewerId, UUID participationId) {
        AuctionParticipation project = earnableProject(participationId);
        int cap = rules.views().dailyCapPerProject();
        if (project.getUserId().equals(viewerId)) {
            return new EarnDtos.ViewStatus(participationId, false, "OWN_PROJECT", 0, cap, 0, false, rules.toDto());
        }
        LocalDate today = rules.today();
        Instant now = clock.instant();
        viewRepository.insertIfMissing(viewerId, project.getUserId(), today, now);
        ProjectView view = viewRepository.findForUpdate(viewerId, project.getUserId(), today).orElseThrow();
        view.startSession(now);
        boolean capReached = view.getPointsEarned() >= cap;
        return new EarnDtos.ViewStatus(participationId, !capReached, capReached ? "CAP_REACHED" : null,
                view.getPointsEarned(), cap, view.getTicks(), view.isBonusAwarded(), rules.toDto());
    }

    /**
     * Tiempo de visita: {@code requestedTicks} tramos de 10 s (normalmente 1; varios si la web del
     * proyecto estuvo un rato abierta en otra ventana y el usuario acaba de volver).
     */
    @Transactional
    public EarnDtos.TickResult tick(UUID viewerId, UUID participationId, int requestedTicks) {
        AuctionParticipation project = earnableProject(participationId);
        if (project.getUserId().equals(viewerId)) {
            throw ApiException.conflict("OWN_PROJECT", "Your own project doesn't earn points.");
        }
        AppProperties.Views config = rules.views();
        int cap = config.dailyCapPerProject();
        LocalDate today = rules.today();

        // 1) Las cuentas de puntos del usuario, bloqueadas: sus premios se procesan de uno en uno
        walletService.lockUserAccounts(viewerId);
        ProjectView view = viewRepository.findForUpdate(viewerId, project.getUserId(), today)
                .orElseThrow(() -> ApiException.conflict("VIEW_NOT_STARTED",
                        "Open the project's website to start earning points."));
        if (view.getPointsEarned() >= cap) {
            return new EarnDtos.TickResult(0, 0, false, view.getPointsEarned(), cap, true,
                    walletService.balance(viewerId).availablePoints());
        }

        // 2) Nadie gana más deprisa que el reloj: como mucho, los tramos de 10 s que caben en el tiempo
        //    real desde que abrió este visor y desde su último premio (de cualquier visita o tarea)
        Instant now = clock.instant();
        Instant reference = AttentionClock.latest(view.getSessionStartedAt(), attentionClock.lastRewardAt(viewerId));
        long elapsedMillis = Duration.between(reference, now).plus(NETWORK_TOLERANCE).toMillis();
        int allowed = (int) Math.min(Integer.MAX_VALUE, elapsedMillis / (config.tickSeconds() * 1000L));
        int wanted = Math.min(Math.max(1, requestedTicks), allowed);
        if (wanted <= 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TICK_TOO_SOON",
                    "10 seconds have not passed yet. Points are counted every 10 seconds.");
        }

        // 3) Puntos de esos tramos (+ bonus al llegar a 60 s), sin pasar del límite diario
        int ticks = 0;
        int points = 0;
        boolean bonus = false;
        while (ticks < wanted && view.getPointsEarned() + points < cap) {
            ticks++;
            points += config.tickPoints();
            if (!view.isBonusAwarded() && !bonus && view.getTicks() + ticks >= config.ticksForBonus()) {
                bonus = true;
                points += config.bonusPoints();
            }
        }
        points = Math.min(points, cap - view.getPointsEarned());
        view.recordTicks(now, ticks, points, bonus);
        viewRepository.flush();

        WalletService.Balance balance = walletService.grant(viewerId, points, LedgerTransactionType.VIEW_REWARD,
                "view:" + view.getId() + ":" + view.getTicks(), "PROJECT_VIEW", view.getId(),
                "Watching an Race project's website" + (bonus ? " (+ " + config.bonusAfterSeconds() + " s bonus)" : ""));
        return new EarnDtos.TickResult(points, ticks, bonus, view.getPointsEarned(), cap, view.getPointsEarned() >= cap,
                balance.availablePoints());
    }

    /** El proyecto tiene que estar compitiendo en la Arena de hoy. */
    private AuctionParticipation earnableProject(UUID participationId) {
        AuctionParticipation participation = participationRepository.findById(participationId)
                .orElseThrow(ApiExceptions::projectNotFound);
        if (!participation.getAuction().isOpen() || participation.getTotalPoints() <= 0) {
            throw ApiException.conflict("PROJECT_NOT_IN_ARENA",
                    "This project is no longer in today's Race, so it doesn't earn points.");
        }
        return participation;
    }

    /** Errores compartidos. */
    static final class ApiExceptions {
        private ApiExceptions() {
        }

        static ApiException projectNotFound() {
            return ApiException.notFound("PROJECT_NOT_FOUND", "That project doesn't exist.");
        }
    }
}
