package com.socialnetwork.chat_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/** Conversations live in Postgres, messages in MongoDB, hence the two explicit repository scans. */
@SpringBootApplication
@EnableJpaRepositories("com.socialnetwork.chat_service.repository.jpa")
@EnableMongoRepositories("com.socialnetwork.chat_service.repository.mongo")
public class ChatServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ChatServiceApplication.class, args);
  }
}
