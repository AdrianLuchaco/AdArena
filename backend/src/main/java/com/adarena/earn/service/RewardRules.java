package com.adarena.earn.service;

import com.adarena.common.config.AppProperties;
import com.adarena.earn.dto.EarnDtos;
import com.adarena.settings.repository.AppSettingsRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** Las reglas para ganar puntos y "qué día es hoy" (en la zona horaria de la Arena: Europe/Madrid). */
@Component
public class RewardRules {

    private final AppProperties.Rewards rewards;
    private final AppSettingsRepository settingsRepository;
    private final Clock clock;

    public RewardRules(AppProperties properties, AppSettingsRepository settingsRepository, Clock clock) {
        this.rewards = properties.rewards();
        this.settingsRepository = settingsRepository;
        this.clock = clock;
    }

    /** Los límites diarios se reinician a las 00:00 de Madrid, igual que la Arena. */
    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settingsRepository.getSettings().zoneId());
    }

    public AppProperties.Views views() {
        return rewards.views();
    }

    public AppProperties.Tasks tasks() {
        return rewards.tasks();
    }

    public long signupBonus() {
        return rewards.signupBonus();
    }

    public EarnDtos.Rules toDto() {
        AppProperties.Views v = rewards.views();
        AppProperties.Tasks t = rewards.tasks();
        return new EarnDtos.Rules(v.tickSeconds(), v.tickPoints(), v.bonusAfterSeconds(), v.bonusPoints(),
                v.dailyCapPerProject(), t.rewardPoints(), t.minSeconds(), t.maxPerDay(), rewards.signupBonus(),
                rewards.winnerBonus());
    }
}
