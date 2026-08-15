package com.taponsusi.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findAllBySellerIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(
            Long sellerId, String entityType, Long entityId);
}