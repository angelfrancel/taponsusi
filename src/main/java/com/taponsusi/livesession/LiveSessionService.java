package com.taponsusi.livesession;

import com.taponsusi.common.exception.ResourceNotFoundException;
import com.taponsusi.livesession.dto.CreateLiveSessionRequest;
import com.taponsusi.livesession.dto.LiveSessionResponse;
import com.taponsusi.security.AuthenticatedSeller;
import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LiveSessionService {

    private final LiveSessionRepository liveSessionRepository;
    private final TenantContext tenantContext;

    @PersistenceContext
    private EntityManager entityManager;

    public LiveSessionService(LiveSessionRepository liveSessionRepository, TenantContext tenantContext) {
        this.liveSessionRepository = liveSessionRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public LiveSessionResponse create(CreateLiveSessionRequest request) {
        tenantContext.applyCurrentSeller(entityManager);

        Long sellerId = AuthenticatedSeller.currentSellerId();
        LiveSession session = new LiveSession(sellerId, request.title(), request.platform());
        LiveSession saved = liveSessionRepository.save(session);

        return LiveSessionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<LiveSessionResponse> listForCurrentSeller() {
        tenantContext.applyCurrentSeller(entityManager);

        Long sellerId = AuthenticatedSeller.currentSellerId();
        return liveSessionRepository.findAllBySellerId(sellerId).stream()
                .map(LiveSessionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LiveSessionResponse getById(Long id) {
        tenantContext.applyCurrentSeller(entityManager);

        Long sellerId = AuthenticatedSeller.currentSellerId();
        LiveSession session = liveSessionRepository.findByIdAndSellerId(id, sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Live session not found: " + id));

        return LiveSessionResponse.from(session);
    }
}