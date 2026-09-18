package com.socialnetwork.notification_service.repository;

import com.socialnetwork.notification_service.model.UserCache;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCacheRepository extends JpaRepository<UserCache, Long> {}
