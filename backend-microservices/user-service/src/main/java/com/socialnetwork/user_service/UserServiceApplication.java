package com.socialnetwork.user_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(
    scanBasePackages = {"com.socialnetwork.user_service", "security", "exception"})
@EnableKafka
@EnableJpaRepositories(
    basePackages = "com.socialnetwork.user_service.repository",
    excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*neo4j.*"))
@EnableNeo4jRepositories(
    basePackages = "com.socialnetwork.user_service.repository.neo4j",
    transactionManagerRef = "neo4jTransactionManager")
public class UserServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(UserServiceApplication.class, args);
  }
}
