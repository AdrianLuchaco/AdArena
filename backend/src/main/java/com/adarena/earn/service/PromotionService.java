package com.adarena.earn.service;

import com.adarena.admin.service.AdminAuditService;
import com.adarena.common.error.ApiException;
import com.adarena.common.error.FieldValidationException;
import com.adarena.common.text.TextSanitizer;
import com.adarena.common.text.WebsiteUrlSanitizer;
import com.adarena.earn.domain.SocialTask;
import com.adarena.earn.domain.SocialTaskReport;
import com.adarena.earn.domain.SocialTaskStatus;
import com.adarena.earn.dto.EarnDtos;
import com.adarena.earn.repository.SocialTaskCompletionRepository;
import com.adarena.earn.repository.SocialTaskReportRepository;
import com.adarena.earn.repository.SocialTaskRepository;
import com.adarena.notification.service.NotificationService;
import com.adarena.notification.service.Notices;
import com.adarena.site.dto.SiteDtos.SiteInfo;
import com.adarena.site.service.SitePreviewRefresher;
import com.adarena.site.service.SitePreviewService;
import com.adarena.user.domain.User;
import com.adarena.user.repository.UserRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Promocionar: cada usuario publica GRATIS sus enlaces (su YouTube, su X, su web…) para que
 * aparezcan en los Créditos extra de los demás. Se publican al momento; el admin puede ocultarlos y
 * los usuarios, denunciarlos.
 */
@Service
public class PromotionService {

    private static final int MIN_TITLE = 3;
    private static final int ADMIN_LIST = 200;

    private final SocialTaskRepository taskRepository;
    private final SocialTaskCompletionRepository completionRepository;
    private final SocialTaskReportRepository reportRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AdminAuditService auditService;
    private final SitePreviewService sitePreviewService;
    private final SitePreviewRefresher sitePreviewRefresher;
    private final RewardRules rules;

    public PromotionService(SocialTaskRepository taskRepository, SocialTaskCompletionRepository completionRepository,
                            SocialTaskReportRepository reportRepository, UserRepository userRepository,
                            NotificationService notificationService, AdminAuditService auditService,
                            SitePreviewService sitePreviewService, SitePreviewRefresher sitePreviewRefresher,
                            RewardRules rules) {
        this.taskRepository = taskRepository;
        this.completionRepository = completionRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.sitePreviewService = sitePreviewService;
        this.sitePreviewRefresher = sitePreviewRefresher;
        this.rules = rules;
    }

    // ------------------------------------------------------------------ dueño

    @Transactional(readOnly = true)
    public EarnDtos.PromotionsOverview mine(UUID ownerId) {
        LocalDate today = rules.today();
        List<SocialTask> tasks = taskRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
        Map<String, UUID> sites = new HashMap<>();
        tasks.forEach(task -> sites.putIfAbsent(task.getUrl(), ownerId));
        Map<String, SiteInfo> siteInfos = sitePreviewService.siteInfos(sites);
        List<EarnDtos.Promotion> promotions = tasks.stream()
                .map(task -> toPromotion(task, completionRepository.countByTaskIdAndTaskDateAndCompletedAtIsNotNull(
                        task.getId(), today), siteInfos.get(task.getUrl())))
                .toList();
        return new EarnDtos.PromotionsOverview(rules.tasks().maxActivePerUser(), rules.tasks().rewardPoints(), promotions);
    }

    @Transactional
    public EarnDtos.Promotion create(UUID ownerId, EarnDtos.PromotionRequest request) {
        String title = TextSanitizer.singleLine(request.title());
        if (title == null || title.length() < MIN_TITLE) {
            throw new FieldValidationException("title", "The title is too short.");
        }
        String description = TextSanitizer.singleLine(request.description());
        if (description != null && description.isEmpty()) {
            description = null;
        }
        String url;
        try {
            url = WebsiteUrlSanitizer.sanitize(request.url());
        } catch (IllegalArgumentException e) {
            throw new FieldValidationException("url", e.getMessage().replace("The website address", "The link").replace("The website", "The link"));
        }
        int max = rules.tasks().maxActivePerUser();
        if (taskRepository.countByOwnerIdAndStatusIn(ownerId, Set.of(SocialTaskStatus.ACTIVE, SocialTaskStatus.PAUSED)) >= max) {
            throw ApiException.conflict("PROMOTION_LIMIT",
                    "You can have at most " + max + " promotions at a time. Delete one to add another.");
        }
        SocialTask task = taskRepository.save(new SocialTask(ownerId, title, description, url, rules.tasks().rewardPoints()));
        // Leemos su web en segundo plano: así sabremos si se puede ver dentro de AdArena y tendrá foto y logo
        sitePreviewRefresher.request(url, ownerId);
        return toPromotion(task, 0, sitePreviewService.toSiteInfo(url, null));
    }

    @Transactional
    public EarnDtos.Promotion pause(UUID ownerId, UUID taskId) {
        SocialTask task = ownTask(ownerId, taskId);
        if (task.getStatus() != SocialTaskStatus.ACTIVE) {
            throw ApiException.conflict("PROMOTION_NOT_ACTIVE", "This promotion is not active.");
        }
        task.pause();
        return toPromotion(task, 0, sitePreviewService.siteInfo(task.getUrl(), ownerId));
    }

    @Transactional
    public EarnDtos.Promotion resume(UUID ownerId, UUID taskId) {
        SocialTask task = ownTask(ownerId, taskId);
        if (task.getStatus() != SocialTaskStatus.PAUSED) {
            throw ApiException.conflict("PROMOTION_NOT_PAUSED",
                    task.getStatus() == SocialTaskStatus.HIDDEN
                            ? "This promotion is hidden for review. Only the AdArena team can publish it again."
                            : "This promotion is already active.");
        }
        task.resume();
        return toPromotion(task, 0, sitePreviewService.siteInfo(task.getUrl(), ownerId));
    }

    @Transactional
    public void delete(UUID ownerId, UUID taskId) {
        taskRepository.delete(ownTask(ownerId, taskId));
    }

    // ------------------------------------------------------------------ administración

    @Transactional(readOnly = true)
    public List<EarnDtos.AdminTask> listForAdmin() {
        List<SocialTask> tasks = taskRepository.findForAdmin(Limit.of(ADMIN_LIST));
        Map<UUID, User> owners = userRepository.findAllById(tasks.stream().map(SocialTask::getOwnerId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return tasks.stream().map(task -> {
            User owner = owners.get(task.getOwnerId());
            List<String> reasons = task.getReports() == 0 && task.getStatus() != SocialTaskStatus.HIDDEN ? List.of()
                    : reportRepository.findByTaskIdOrderByCreatedAtDesc(task.getId()).stream()
                    .map(SocialTaskReport::getReason).limit(10).toList();
            return new EarnDtos.AdminTask(task.getId(), task.getPlatform(), task.getTitle(), task.getDescription(),
                    task.getUrl(), task.getStatus(), task.getHiddenReason(), task.getReports(), task.getCompletions(),
                    owner == null ? null : owner.getEmail(), owner == null ? null : owner.getDisplayName(),
                    task.getCreatedAt(), reasons);
        }).toList();
    }

    @Transactional
    public void hide(UUID adminId, UUID taskId, String rawReason) {
        String reason = TextSanitizer.singleLine(rawReason);
        if (reason == null || reason.length() < 3) {
            throw ApiException.badRequest("REASON_REQUIRED", "Briefly explain the reason (we will send it to the owner).");
        }
        SocialTask task = taskRepository.findForUpdate(taskId)
                .orElseThrow(() -> ApiException.notFound("TASK_NOT_FOUND", "That link doesn't exist."));
        task.hide(reason);
        auditService.record(adminId, "TASK_HIDDEN", "SOCIAL_TASK", taskId, Map.of("reason", reason, "url", task.getUrl()));
        userRepository.findById(task.getOwnerId())
                .ifPresent(owner -> notificationService.notify(owner, Notices.taskHidden(task.getTitle(), reason)));
    }

    @Transactional
    public void restore(UUID adminId, UUID taskId) {
        SocialTask task = taskRepository.findForUpdate(taskId)
                .orElseThrow(() -> ApiException.notFound("TASK_NOT_FOUND", "That link doesn't exist."));
        task.restore();
        auditService.record(adminId, "TASK_RESTORED", "SOCIAL_TASK", taskId, Map.of("url", task.getUrl()));
    }

    // ------------------------------------------------------------------ piezas internas

    private SocialTask ownTask(UUID ownerId, UUID taskId) {
        return taskRepository.findById(taskId)
                .filter(task -> task.getOwnerId().equals(ownerId)) // la de otro usuario "no existe" para ti
                .orElseThrow(() -> ApiException.notFound("PROMOTION_NOT_FOUND", "That promotion doesn't exist."));
    }

    private static EarnDtos.Promotion toPromotion(SocialTask task, long visitsToday, SiteInfo site) {
        return new EarnDtos.Promotion(task.getId(), task.getPlatform(), task.getPlatform().getLabel(), task.getTitle(),
                task.getDescription(), task.getUrl(), task.getStatus(), task.getHiddenReason(), task.getRewardPoints(),
                task.getCompletions(), visitsToday, task.getCreatedAt(), site);
    }
}
