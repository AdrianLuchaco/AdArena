package com.adarena.user.service;

import com.adarena.common.config.AppProperties;
import com.adarena.user.domain.Role;
import com.adarena.user.domain.User;
import com.adarena.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Crea tu cuenta de administrador al arrancar, a partir de ADMIN_EMAIL y ADMIN_PASSWORD.
 * Solo actúa si ese email todavía no existe: nunca modifica ni asciende cuentas existentes
 * (así nadie puede convertirse en admin registrándose antes con tu email).
 * Una vez creada, puedes quitar ADMIN_PASSWORD de las variables de entorno.
 */
@Component
@Order(0)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final Clock clock;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          AppProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = properties.admin();
        if (admin == null || !admin.isConfigured()) {
            return;
        }
        String email = User.normalizeEmail(admin.email());
        userRepository.findByEmail(email).ifPresentOrElse(
                existing -> {
                    if (existing.getRole() != Role.ADMIN) {
                        log.warn("ADMIN_EMAIL {} belongs to a non-admin account; it was NOT promoted", email);
                    }
                },
                () -> {
                    if (admin.password().length() < MIN_ADMIN_PASSWORD_LENGTH) {
                        throw new IllegalStateException("ADMIN_PASSWORD must have at least 12 characters");
                    }
                    String name = admin.displayName() == null || admin.displayName().isBlank()
                            ? "Administrador" : admin.displayName();
                    userRepository.save(new User(email, passwordEncoder.encode(admin.password()), name,
                            Role.ADMIN, properties.legal().termsVersion(), clock.instant()));
                    log.info("Admin account {} created", email);
                });
    }
}
