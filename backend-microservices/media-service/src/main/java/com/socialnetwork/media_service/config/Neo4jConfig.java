package com.socialnetwork.media_service.config;

import jakarta.persistence.EntityManagerFactory;
import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

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
      Driver driver, DatabaseSelectionProvider databaseSelectionProvider) {
    return new Neo4jTransactionManager(driver, databaseSelectionProvider);
  }
}
