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
    private final LiveSessionStateMachine stateMachine;
    private final LiveSessionPreflightService preflightService;

    @PersistenceContext
    private EntityManager entityManager;

    public LiveSessionService(
            LiveSessionRepository liveSessionRepository,
            TenantContext tenantContext,
            LiveSessionStateMachine stateMachine,
            LiveSessionPreflightService preflightService
    ) {
        this.liveSessionRepository = liveSessionRepository;
        this.tenantContext = tenantContext;
        this.stateMachine = stateMachine;
        this.preflightService = preflightService;
    }

    @Transactional(readOnly = true)
    public SessionPreflightResponse preflight(Long sessionId) {
        return preflightService.evaluate(sessionId);
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

    @Transactional
    public LiveSessionResponse transitionStatus(
            Long sessionId,
            LiveSessionStatus requestedStatus
    ) {
        tenantContext.applyCurrentSeller(entityManager);

        Long sellerId = AuthenticatedSeller.currentSellerId();

        LiveSession session = liveSessionRepository
                .findForStatusTransition(sessionId, sellerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Live session not found: " + sessionId
                        )
                );

        LiveSessionStatus currentStatus = session.getStatus();

        stateMachine.validate(currentStatus, requestedStatus);

        if (currentStatus == LiveSessionStatus.DRAFT
                && requestedStatus == LiveSessionStatus.READY) {
            preflightService.requireReady(sessionId);
        }

        session.setStatus(requestedStatus);

        if (requestedStatus == LiveSessionStatus.LIVE
                && session.getStartedAt() == null) {
            session.setStartedAt(java.time.Instant.now());
        }

        if (requestedStatus == LiveSessionStatus.ENDED) {
            session.setEndedAt(java.time.Instant.now());
        }

        session.setStatus(requestedStatus);

        LiveSession saved = liveSessionRepository.save(session);

        return LiveSessionResponse.from(saved);
    }
}