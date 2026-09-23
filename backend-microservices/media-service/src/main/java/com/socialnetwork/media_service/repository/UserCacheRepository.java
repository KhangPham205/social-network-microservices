package com.socialnetwork.media_service.repository;

import com.socialnetwork.media_service.model.UserCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Read model of user identities, fed by {@code USER_CREATED} and {@code PROFILE_UPDATED}. */
@Repository
public interface UserCacheRepository extends JpaRepository<UserCache, Long> {}
