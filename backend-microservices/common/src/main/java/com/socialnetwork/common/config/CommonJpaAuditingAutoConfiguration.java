package com.socialnetwork.common.config;

import com.socialnetwork.common.security.SecurityUtils;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing so {@code BaseEntity.createdBy/updatedBy} hold the id of the
 * authenticated user ("system" for Kafka listeners and schedulers).
 */
@AutoConfiguration(
    afterName = "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration")
@ConditionalOnClass(name = "jakarta.persistence.EntityManagerFactory")
@ConditionalOnBean(type = "jakarta.persistence.EntityManagerFactory")
@EnableJpaAuditing(auditorAwareRef = "commonAuditorAware")
public class CommonJpaAuditingAutoConfiguration {

  public static final String SYSTEM_AUDITOR = "system";

  @Bean
  @ConditionalOnMissingBean(name = "commonAuditorAware")
  public AuditorAware<String> commonAuditorAware() {
    return () -> Optional.of(SecurityUtils.findCurrentUserId().map(String::valueOf).orElse(SYSTEM_AUDITOR));
  }
}
