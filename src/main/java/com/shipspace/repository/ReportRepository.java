package com.shipspace.repository;

import com.shipspace.model.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends MongoRepository<Report, String> {
    Page<Report> findByStatus(Report.Status status, Pageable pageable);
    List<Report> findByTargetId(String targetId);
    long countByStatus(Report.Status status);
}
