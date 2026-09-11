package com.taponsusi.audit;

import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final TenantContext tenantContext;

    @PersistenceContext
    private EntityManager entityManager;

    public AuditService(
            AuditEventRepository auditEventRepository,
            TenantContext tenantContext
    ) {
        this.auditEventRepository = auditEventRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public void record(
            Long sellerId,
            String eventType,
            String entityType,
            Long entityId,
            String detail
    ) {
        tenantContext.applyCurrentSeller(entityManager, sellerId);

        AuditEvent event = new AuditEvent(
                sellerId,
                eventType,
                entityType,
                entityId,
                detail
        );

        auditEventRepository.save(event);
    }
}