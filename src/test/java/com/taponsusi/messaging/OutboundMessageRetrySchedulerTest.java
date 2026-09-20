package com.taponsusi.messaging;

import com.taponsusi.seller.Seller;
import com.taponsusi.seller.SellerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboundMessageRetrySchedulerTest {

    private SellerRepository sellerRepository;
    private OutboundMessageRetryCandidateReader candidateReader;
    private OutboundMessageDispatcher dispatcher;
    private OutboundMessageRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        sellerRepository =
                mock(SellerRepository.class);

        candidateReader =
                mock(
                        OutboundMessageRetryCandidateReader.class
                );

        dispatcher =
                mock(OutboundMessageDispatcher.class);

        scheduler =
                new OutboundMessageRetryScheduler(
                        sellerRepository,
                        candidateReader,
                        dispatcher,
                        5
                );
    }

    @Test
    void retriesCandidatesForEverySeller() {
        Seller sellerA = mock(Seller.class);
        Seller sellerB = mock(Seller.class);

        when(sellerA.getId()).thenReturn(101L);
        when(sellerB.getId()).thenReturn(202L);

        when(sellerRepository.findAll())
                .thenReturn(
                        List.of(sellerA, sellerB)
                );

        when(candidateReader.findCandidates(101L, 5))
                .thenReturn(
                        List.of(1001L, 1002L)
                );

        when(candidateReader.findCandidates(202L, 5))
                .thenReturn(
                        List.of(2001L)
                );

        scheduler.retryMessages();

        verify(dispatcher).dispatch(
                101L,
                1001L
        );

        verify(dispatcher).dispatch(
                101L,
                1002L
        );

        verify(dispatcher).dispatch(
                202L,
                2001L
        );
    }

    @Test
    void oneSellerFailureDoesNotStopOtherSellers() {
        Seller sellerA = mock(Seller.class);
        Seller sellerB = mock(Seller.class);

        when(sellerA.getId()).thenReturn(101L);
        when(sellerB.getId()).thenReturn(202L);

        when(sellerRepository.findAll())
                .thenReturn(
                        List.of(sellerA, sellerB)
                );

        when(candidateReader.findCandidates(101L, 5))
                .thenThrow(
                        new RuntimeException(
                                "Seller A database failure"
                        )
                );

        when(candidateReader.findCandidates(202L, 5))
                .thenReturn(
                        List.of(2001L)
                );

        scheduler.retryMessages();

        verify(dispatcher).dispatch(
                202L,
                2001L
        );
    }
}