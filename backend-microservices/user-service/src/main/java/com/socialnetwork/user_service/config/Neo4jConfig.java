package com.socialnetwork.user_service.config;

import jakarta.persistence.EntityManagerFactory;
import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Two stores mean two transaction managers. JPA stays primary so {@code @Transactional} without a
 * qualifier keeps its usual meaning; the Neo4j one is referenced by name from the repositories.
 */
@Configuration
public class Neo4jConfig {

  @Bean(name = "transactionManager")
  @Primary
  public PlatformTransactionManager jpaTransactionManager(
      EntityManagerFactory entityManagerFactory) {
    return new JpaTransactionManager(entityManagerFactory);
  }

  @Bean(name = "neo4jTransactionManager")
  public PlatformTransactionManager neo4jTransactionManager(
      Driver driver, DatabaseSelectionProvider databaseNameProvider) {
    return new Neo4jTransactionManager(driver, databaseNameProvider);
  }
}
