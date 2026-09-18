package com.socialnetwork.moderation_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class ModerationServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ModerationServiceApplication.class, args);
  }
}
