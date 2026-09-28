package com.adarena.earn.service;

import com.adarena.common.error.ApiException;
import com.adarena.common.text.TextSanitizer;
import com.adarena.earn.domain.SocialTask;
import com.adarena.earn.domain.SocialTaskCompletion;
import com.adarena.earn.domain.SocialTaskReport;
import com.adarena.earn.dto.EarnDtos;
import com.adarena.earn.repository.SocialTaskCompletionRepository;
import com.adarena.earn.repository.SocialTaskReportRepository;
import com.adarena.earn.repository.SocialTaskRepository;
import com.adarena.notification.service.NotificationService;
import com.adarena.notification.service.Notices;
import com.adarena.site.dto.SiteDtos.SiteInfo;
import com.adarena.site.service.SitePreviewService;
import com.adarena.user.repository.UserRepository;
import com.adarena.wallet.domain.LedgerTransactionType;
import com.adarena.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Créditos extra: los usuarios ganan puntos VISITANDO los enlaces que promocionan otros usuarios.
 * <ol>
 *   <li>Al abrir el enlace en el visor (dentro de AdArena o en su propia ventana), el servidor
 *       apunta la hora ({@link #start}).</li>
 *   <li>Tras 10 s mirándolo, la web reclama los puntos ({@link #claim}). El servidor solo los da si
 *       han pasado al menos 10 s (con SU reloj) desde que se abrió y desde el último premio del
 *       usuario ({@link AttentionClock}: abrir diez tareas a la vez no hace ganar más deprisa).</li>
 *   <li>Cada tarea, una vez al día por usuario; como mucho 10 tareas con recompensa al día.</li>
 * </ol>
 * No se puede comprobar qué hace el usuario en la otra web: por eso se premia la VISITA, nunca
 * "dar like" o "seguir" (además, YouTube y X prohíben recompensar esas acciones).
 */
@Service
public class SocialTaskService {

    private static final Logger log = LoggerFactory.getLogger(SocialTaskService.class);
    private static final int MAX_LISTED = 50;
    private static final String AUTO_HIDE_REASON =
            "Several people reported it. We will review it and, if everything is fine, it will be published again.";

    private final SocialTaskRepository taskRepository;
    private final SocialTaskCompletionRepository completionRepository;
    private final SocialTaskReportRepository reportRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final SitePreviewService sitePreviewService;
    private final AttentionClock attentionClock;
    private final RewardRules rules;
    private final Clock clock;

    public SocialTaskService(SocialTaskRepository taskRepository, SocialTaskCompletionRepository completionRepository,
                             SocialTaskReportRepository reportRepository, UserRepository userRepository,
                             WalletService walletService, NotificationService notificationService,
                             SitePreviewService sitePreviewService, AttentionClock attentionClock, RewardRules rules,
                             Clock clock) {
        this.taskRepository = taskRepository;
        this.completionRepository = completionRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.notificationService = notificationService;
        this.sitePreviewService = sitePreviewService;
        this.attentionClock = attentionClock;
        this.rules = rules;
        this.clock = clock;
    }

    /** Las tareas de hoy para un usuario: primero las pendientes, después las ya hechas. */
    @Transactional(readOnly = true)
    public EarnDtos.TasksOverview list(UUID userId) {
        LocalDate today = rules.today();
        Map<UUID, SocialTaskCompletion> mine = completionRepository.findByUserIdAndTaskDate(userId, today).stream()
                .collect(Collectors.toMap(SocialTaskCompletion::getTaskId, Function.identity()));
        List<SocialTask> available = taskRepository.findAvailableFor(userId, Limit.of(MAX_LISTED));
        Map<String, UUID> sites = new HashMap<>();
        available.forEach(task -> sites.putIfAbsent(task.getUrl(), task.getOwnerId()));
        Map<String, SiteInfo> siteInfos = sitePreviewService.siteInfos(sites);
        List<EarnDtos.TaskItem> tasks = available.stream()
                .map(task -> toItem(task, mine.get(task.getId()), siteInfos.get(task.getUrl())))
                .sorted(Comparator.comparing(item -> "DONE".equals(item.state())))
                .toList();
        return new EarnDtos.TasksOverview(
                completionRepository.countByUserIdAndTaskDateAndCompletedAtIsNotNull(userId, today),
                rules.tasks().maxPerDay(), completionRepository.sumPointsAwarded(userId, today), rules.toDto(), tasks);
    }

    /** El usuario pulsa la tarea: se apunta la hora y se le devuelve el enlace. */
    @Transactional
    public EarnDtos.TaskStarted start(UUID userId, UUID taskId) {
        SocialTask task = availableTask(userId, taskId);
        LocalDate today = rules.today();
        Instant now = clock.instant();
        SocialTaskCompletion existing = completionRepository.findForUpdate(taskId, userId, today).orElse(null);
        if (existing != null && existing.isCompleted()) {
            throw ApiException.conflict("TASK_ALREADY_DONE", "You already did this one today. Come back tomorrow!");
        }
        requireDailyLimitNotReached(userId, today);
        boolean created = existing == null && completionRepository.insertIfMissing(taskId, userId, today, now) == 1;
        SocialTaskCompletion completion = existing != null ? existing
                : completionRepository.findForUpdate(taskId, userId, today).orElseThrow();
        if (!created) {
            completion.restart(now);
        }
        return new EarnDtos.TaskStarted(task.getId(), task.getUrl(), rules.tasks().minSeconds(), completion.getStartedAt());
    }

    /** El usuario vuelve a AdArena: si ha pasado el tiempo mínimo, se le dan los puntos. */
    @Transactional
    public EarnDtos.TaskClaimed claim(UUID userId, UUID taskId) {
        SocialTask task = availableTask(userId, taskId);
        LocalDate today = rules.today();
        // Las cuentas del usuario bloqueadas: sus reclamaciones se procesan de una en una
        walletService.lockUserAccounts(userId);
        SocialTaskCompletion completion = completionRepository.findForUpdate(taskId, userId, today)
                .orElseThrow(() -> ApiException.conflict("TASK_NOT_STARTED", "Open the link first."));
        if (completion.isCompleted()) {
            throw ApiException.conflict("TASK_ALREADY_DONE", "You already did this one today. Come back tomorrow!");
        }
        Instant now = clock.instant();
        Instant reference = AttentionClock.latest(completion.getStartedAt(), attentionClock.lastRewardAt(userId));
        long waited = Duration.between(reference, now).toSeconds();
        int minSeconds = rules.tasks().minSeconds();
        if (waited < minSeconds) {
            throw ApiException.conflict("TASK_TOO_SOON",
                    "Watch the page for at least " + minSeconds + " seconds. " + (minSeconds - waited) + " to go.");
        }
        requireDailyLimitNotReached(userId, today);

        int points = task.getRewardPoints();
        completion.complete(now, points);
        taskRepository.incrementCompletions(taskId);
        WalletService.Balance balance = walletService.grant(userId, points, LedgerTransactionType.TASK_REWARD,
                "task:" + completion.getId(), "SOCIAL_TASK", task.getId(), "Bonus link: " + task.getTitle());
        return new EarnDtos.TaskClaimed(task.getId(), points,
                completionRepository.countByUserIdAndTaskDateAndCompletedAtIsNotNull(userId, today),
                rules.tasks().maxPerDay(), balance.availablePoints());
    }

    /**
     * Denunciar una tarea (enlace roto, engañoso, peligroso…). Con 3 denuncias de usuarios
     * distintos se oculta sola hasta que el admin la revise.
     */
    @Transactional
    public void report(UUID userId, UUID taskId, String rawReason) {
        String reason = TextSanitizer.singleLine(rawReason);
        if (reason == null || reason.length() < 3) {
            throw ApiException.badRequest("REASON_REQUIRED", "Tell us what's wrong with this link.");
        }
        SocialTask task = taskRepository.findForUpdate(taskId)
                .orElseThrow(() -> ApiException.notFound("TASK_NOT_FOUND", "That link doesn't exist."));
        if (task.getOwnerId().equals(userId)) {
            throw ApiException.conflict("OWN_TASK", "You can't report your own promotion.");
        }
        if (reportRepository.existsByTaskIdAndUserId(taskId, userId)) {
            throw ApiException.conflict("ALREADY_REPORTED", "You already reported this link. Thank you.");
        }
        reportRepository.save(new SocialTaskReport(taskId, userId, reason));
        taskRepository.incrementReports(taskId);
        taskRepository.flush();
        int reports = task.getReports() + 1;
        if (task.isActive() && reports >= rules.tasks().reportsToHide()) {
            task.hide(AUTO_HIDE_REASON);
            userRepository.findById(task.getOwnerId())
                    .ifPresent(owner -> notificationService.notify(owner, Notices.taskHidden(task.getTitle(), AUTO_HIDE_REASON)));
            log.warn("Social task {} hidden automatically after {} reports", taskId, reports);
        }
    }

    // ------------------------------------------------------------------ piezas internas

    private SocialTask availableTask(UUID userId, UUID taskId) {
        SocialTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> ApiException.notFound("TASK_NOT_FOUND", "That link doesn't exist."));
        if (task.getOwnerId().equals(userId)) {
            throw ApiException.conflict("OWN_TASK", "Your own promotions don't earn points.");
        }
        if (!task.isActive()) {
            throw ApiException.conflict("TASK_UNAVAILABLE", "This link is no longer available.");
        }
        return task;
    }

    private void requireDailyLimitNotReached(UUID userId, LocalDate today) {
        int max = rules.tasks().maxPerDay();
        if (completionRepository.countByUserIdAndTaskDateAndCompletedAtIsNotNull(userId, today) >= max) {
            throw ApiException.conflict("TASK_DAILY_LIMIT",
                    "You have done all " + max + " bonus links for today. More tomorrow!");
        }
    }

    private static EarnDtos.TaskItem toItem(SocialTask task, SocialTaskCompletion completion, SiteInfo site) {
        String state = completion == null ? "AVAILABLE" : completion.isCompleted() ? "DONE" : "STARTED";
        return new EarnDtos.TaskItem(task.getId(), task.getPlatform(), task.getPlatform().getLabel(), task.getTitle(),
                task.getDescription(), task.getUrl(), task.getRewardPoints(), state,
                completion == null ? null : completion.getStartedAt(), site);
    }
}
