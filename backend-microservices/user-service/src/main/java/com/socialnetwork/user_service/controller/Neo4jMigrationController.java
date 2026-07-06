package com.socialnetwork.user_service.controller;

import com.socialnetwork.user_service.service.Neo4jMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/neo4j")
@RequiredArgsConstructor
public class Neo4jMigrationController {

  private final Neo4jMigrationService neo4jMigrationService;

  @PostMapping("/sync")
  public ResponseEntity<String> syncData() {
    String result = neo4jMigrationService.runMigration();
    return ResponseEntity.ok(result);
  }
}
