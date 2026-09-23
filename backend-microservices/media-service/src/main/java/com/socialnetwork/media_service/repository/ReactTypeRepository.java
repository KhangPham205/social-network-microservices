package com.socialnetwork.media_service.repository;

import com.socialnetwork.media_service.model.ReactType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReactTypeRepository extends JpaRepository<ReactType, Long> {}
