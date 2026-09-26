package com.publifi.settings.domain;

import com.publifi.auction.domain.AuctionRules;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Configuración global (fila única con id = 1), editable desde el panel de administración.
 */
@Entity
@Table(name = "app_settings")
public class AppSettings {

    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    private long minBidCents;
    private long minIncrementCents;
    private int carryOverPercent;
    private LocalTime closeTime;
    private String timeZone;
    private int antiSnipingWindowSeconds;
    private int antiSnipingExtensionSeconds;
    private int antiSnipingMaxExtensions;
    private Instant updatedAt;
    private UUID updatedBy;

    @Version
    private long version;

    protected AppSettings() {
        // JPA
    }

    /** Copia de las reglas que se congela en cada subasta nueva. */
    public AuctionRules toAuctionRules() {
        return new AuctionRules(minBidCents, minIncrementCents, carryOverPercent,
                antiSnipingWindowSeconds, antiSnipingExtensionSeconds, antiSnipingMaxExtensions);
    }

    public void update(long minBidCents, long minIncrementCents, int carryOverPercent, LocalTime closeTime,
                       String timeZone, int antiSnipingWindowSeconds, int antiSnipingExtensionSeconds,
                       int antiSnipingMaxExtensions, UUID adminId, Instant now) {
        ZoneId.of(timeZone); // lanza excepción si la zona horaria no existe
        this.minBidCents = minBidCents;
        this.minIncrementCents = minIncrementCents;
        this.carryOverPercent = carryOverPercent;
        this.closeTime = closeTime;
        this.timeZone = timeZone;
        this.antiSnipingWindowSeconds = antiSnipingWindowSeconds;
        this.antiSnipingExtensionSeconds = antiSnipingExtensionSeconds;
        this.antiSnipingMaxExtensions = antiSnipingMaxExtensions;
        this.updatedBy = adminId;
        this.updatedAt = now;
    }

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }

    public Integer getId() {
        return id;
    }

    public long getMinBidCents() {
        return minBidCents;
    }

    public long getMinIncrementCents() {
        return minIncrementCents;
    }

    public int getCarryOverPercent() {
        return carryOverPercent;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public int getAntiSnipingWindowSeconds() {
        return antiSnipingWindowSeconds;
    }

    public int getAntiSnipingExtensionSeconds() {
        return antiSnipingExtensionSeconds;
    }

    public int getAntiSnipingMaxExtensions() {
        return antiSnipingMaxExtensions;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
