package com.socialnetwork.user_service.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

/**
 * The service owns two stores, so the repository packages have to be split explicitly: everything
 * under {@code repository} is JPA except {@code repository.neo4j}.
 */
@Configuration
@EnableJpaRepositories(
    basePackages = "com.socialnetwork.user_service.repository",
    excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*neo4j.*"))
@EnableNeo4jRepositories(
    basePackages = "com.socialnetwork.user_service.repository.neo4j",
    transactionManagerRef = "neo4jTransactionManager")
public class DatabaseConfig {}
