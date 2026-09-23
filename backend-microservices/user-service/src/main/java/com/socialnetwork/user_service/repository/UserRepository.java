package com.socialnetwork.user_service.repository;

import com.socialnetwork.user_service.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

  /** Redeclared to load {@code userInfo} with the page instead of one select per row. */
  @Override
  @EntityGraph(attributePaths = "userInfo")
  Page<User> findAll(Specification<User> spec, Pageable pageable);
}
