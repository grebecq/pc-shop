package dev.pcshop.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// Аудит сущностей
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
