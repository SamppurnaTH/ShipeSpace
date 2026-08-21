package com.shipspace.repository;

import com.shipspace.model.CollaborationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CollaborationRequestRepository extends MongoRepository<CollaborationRequest, String> {

    Page<CollaborationRequest> findByOwnerIdAndStatus(
            String ownerId, CollaborationRequest.Status status, Pageable pageable);

    Page<CollaborationRequest> findByOwnerId(String ownerId, Pageable pageable);

    Page<CollaborationRequest> findByRequesterId(String requesterId, Pageable pageable);

    boolean existsByRequesterIdAndProjectId(String requesterId, String projectId);

    Optional<CollaborationRequest> findByRequesterIdAndProjectId(String requesterId, String projectId);
}
