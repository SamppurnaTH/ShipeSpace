package com.shipspace.repository;

import com.shipspace.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Notification document operations.
 */
@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<Notification> findByUserId(String userId, Pageable pageable);

    long countByUserIdAndReadFalse(String userId);

    // Used by bulk markAllAsRead — do NOT add Pageable.unpaged() calls here
}
