package com.adarena.admin.service;

import com.adarena.admin.dto.AdminDtos;
import com.adarena.admin.repository.AdminAuditLogRepository;
import com.adarena.adslot.domain.AdSlotStatus;
import com.adarena.adslot.repository.AdSlotRepository;
import com.adarena.auction.domain.AuctionStatus;
import com.adarena.auction.repository.AuctionParticipationRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.earn.domain.SocialTaskStatus;
import com.adarena.earn.repository.SocialTaskRepository;
import com.adarena.notification.domain.OutboxEmailStatus;
import com.adarena.notification.repository.OutboxEmailRepository;
import com.adarena.notification.service.MailDelivery;
import com.adarena.settings.repository.AppSettingsRepository;
import com.adarena.user.domain.User;
import com.adarena.user.repository.UserRepository;
import com.adarena.wallet.repository.LedgerEntryRepository;
import com.adarena.wallet.service.WalletService;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Datos de la portada del panel de administración y del registro de auditoría. */
@Service
public class AdminOverviewService {

    private final WalletService walletService;
    private final LedgerEntryRepository entryRepository;
    private final UserRepository userRepository;
    private final AdSlotRepository adSlotRepository;
    private final SocialTaskRepository taskRepository;
    private final OutboxEmailRepository outboxRepository;
    private final AuctionRepository auctionRepository;
    private final AuctionParticipationRepository participationRepository;
    private final AdminAuditLogRepository auditRepository;
    private final AppSettingsRepository settingsRepository;
    private final MailDelivery mailDelivery;
    private final Clock clock;

    public AdminOverviewService(WalletService walletService, LedgerEntryRepository entryRepository,
                                UserRepository userRepository, AdSlotRepository adSlotRepository,
                                SocialTaskRepository taskRepository, OutboxEmailRepository outboxRepository,
                                AuctionRepository auctionRepository, AuctionParticipationRepository participationRepository,
                                AdminAuditLogRepository auditRepository, AppSettingsRepository settingsRepository,
                                MailDelivery mailDelivery, Clock clock) {
        this.walletService = walletService;
        this.entryRepository = entryRepository;
        this.userRepository = userRepository;
        this.adSlotRepository = adSlotRepository;
        this.taskRepository = taskRepository;
        this.outboxRepository = outboxRepository;
        this.auctionRepository = auctionRepository;
        this.participationRepository = participationRepository;
        this.auditRepository = auditRepository;
        this.settingsRepository = settingsRepository;
        this.mailDelivery = mailDelivery;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminDtos.Overview overview() {
        WalletService.PlatformTotals totals = walletService.platformTotals();
        AdminDtos.OpenRound openRound = auctionRepository.findFirstByStatus(AuctionStatus.OPEN)
                .map(round -> new AdminDtos.OpenRound(round.getId(), round.getAuctionDate(), round.getEndsAt(),
                        participationRepository.countActiveBidders(round.getId()),
                        participationRepository.sumTotals(round.getId())))
                .orElse(null);
        // "Hoy" empieza a las 00:00 de Madrid, igual que los límites diarios
        var zone = settingsRepository.getSettings().zoneId();
        Instant startOfToday = LocalDate.ofInstant(clock.instant(), zone).atStartOfDay(zone).toInstant();
        return new AdminDtos.Overview(totals.spentPoints(), totals.issuedPoints(),
                entryRepository.pointsIssuedSince(startOfToday), totals.usersAvailablePoints(),
                totals.usersReservedPoints(), totals.ledgerMismatches(), userRepository.count(),
                adSlotRepository.countByStatus(AdSlotStatus.PENDING_REVIEW),
                taskRepository.countByStatus(SocialTaskStatus.ACTIVE), taskRepository.countReportedVisible(),
                taskRepository.countByStatus(SocialTaskStatus.HIDDEN),
                outboxRepository.countByStatus(OutboxEmailStatus.FAILED), mailDelivery.isReal(), openRound);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.AuditEntry> auditLog(int limit) {
        var entries = auditRepository.findAllByOrderByCreatedAtDesc(Limit.of(Math.clamp(limit, 1, 200)));
        Map<UUID, User> admins = userRepository.findAllById(entries.stream().map(e -> e.getAdminId()).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return entries.stream().map(entry -> new AdminDtos.AuditEntry(entry.getId(),
                admins.containsKey(entry.getAdminId()) ? admins.get(entry.getAdminId()).getDisplayName() : null,
                entry.getAction(), entry.getTargetType(), entry.getTargetId(), entry.getDetails(), entry.getCreatedAt()))
                .toList();
    }
}
