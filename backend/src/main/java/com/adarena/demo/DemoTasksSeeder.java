package com.adarena.demo;

import com.adarena.common.config.AppProperties;
import com.adarena.earn.domain.SocialTask;
import com.adarena.earn.repository.SocialTaskRepository;
import com.adarena.user.domain.User;
import com.adarena.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SOLO EN DESARROLLO. Si todavía no hay ninguna promoción, crea tres de ejemplo (de los
 * anunciantes de demostración) para que "Créditos extra" no aparezca vacío. Apuntan a perfiles
 * oficiales públicos de YouTube y X y a una web de ejemplo.
 */
@Component
@Profile("dev")
@ConditionalOnBooleanProperty("app.demo-data.enabled")
@Order(20)
public class DemoTasksSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoTasksSeeder.class);

    private final SocialTaskRepository taskRepository;
    private final UserRepository userRepository;
    private final AppProperties properties;

    public DemoTasksSeeder(SocialTaskRepository taskRepository, UserRepository userRepository, AppProperties properties) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (taskRepository.count() > 0) {
            return;
        }
        int reward = properties.rewards().tasks().rewardPoints();
        create("demo-cafe@adarena.local", DemoContent.TASK_YOUTUBE_TITLE, DemoContent.TASK_YOUTUBE_TEXT,
                "https://www.youtube.com/@YouTube", reward);
        create("demo-bicis@adarena.local", DemoContent.TASK_X_TITLE, DemoContent.TASK_X_TEXT, "https://x.com/X", reward);
        create("demo-huerta@adarena.local", DemoContent.TASK_GARDEN_TITLE, DemoContent.TASK_GARDEN_TEXT, "https://www.example.com/huerta-viva", reward);
        log.info("Demo social tasks created");
    }

    private void create(String email, String title, String description, String url, int reward) {
        userRepository.findByEmail(User.normalizeEmail(email))
                .ifPresent(user -> taskRepository.save(new SocialTask(user.getId(), title, description, url, reward)));
    }
}
