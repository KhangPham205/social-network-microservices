package com.socialnetwork.user_service.service;

public interface Neo4jMigrationService {

  /**
   * Rebuilds the recommendation graph from the relational data. Admin-only maintenance task, meant
   * to be run once after an import or when the graph drifted.
   *
   * @return a short human readable summary
   */
  String runMigration();
}
