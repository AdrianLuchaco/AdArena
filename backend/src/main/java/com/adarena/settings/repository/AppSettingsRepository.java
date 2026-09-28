package com.adarena.settings.repository;

import com.adarena.settings.domain.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingsRepository extends JpaRepository<AppSettings, Integer> {

    default AppSettings getSettings() {
        return findById(AppSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("app_settings row is missing"));
    }
}
