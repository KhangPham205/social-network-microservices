package com.socialnetwork.notification_service.repository;

import com.socialnetwork.notification_service.model.UserCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface UserCacheRepository
    extends JpaRepository<UserCache, Long>, JpaSpecificationExecutor<UserCache> {}
