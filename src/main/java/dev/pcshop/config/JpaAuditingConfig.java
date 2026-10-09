package dev.pcshop.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Заполняет поля с @CreatedDate и @LastModifiedDate при сохранении сущностей. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
