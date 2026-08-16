package com.taponsusi.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void record(Long sellerId, String eventType, String entityType, Long entityId, String detail) {
        AuditEvent event = new AuditEvent(sellerId, eventType, entityType, entityId, detail);
        auditEventRepository.save(event);
    }
}