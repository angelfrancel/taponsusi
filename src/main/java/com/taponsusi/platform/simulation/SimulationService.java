package com.taponsusi.platform.simulation;

import com.taponsusi.common.exception.ResourceNotFoundException;
import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.inventory.InventoryItemRepository;
import com.taponsusi.order.Order;
import com.taponsusi.parser.CommentEvent;
import com.taponsusi.parser.PurchaseIntent;
import com.taponsusi.parser.PurchaseIntentParser;
import com.taponsusi.parser.PurchaseIntentRejectedException;
import com.taponsusi.platform.simulation.dto.SimulationRequest;
import com.taponsusi.platform.simulation.dto.SimulationResult;
import com.taponsusi.reservation.OutOfStockException;
import com.taponsusi.reservation.ReservationContendedException;
import com.taponsusi.reservation.ReservationEngine;
import com.taponsusi.security.AuthenticatedSeller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final InventoryItemRepository inventoryItemRepository;
    private final PurchaseIntentParser purchaseIntentParser;
    private final ReservationEngine reservationEngine;

    public SimulationService(InventoryItemRepository inventoryItemRepository,
                             PurchaseIntentParser purchaseIntentParser,
                             ReservationEngine reservationEngine) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.purchaseIntentParser = purchaseIntentParser;
        this.reservationEngine = reservationEngine;
    }

    public SimulationResult run(SimulationRequest request) {
        Long sellerId = AuthenticatedSeller.currentSellerId();

        InventoryItem item = inventoryItemRepository
                .findBySellerIdAndSessionIdAndReservationCode(
                        sellerId, request.sessionId(), request.inventoryCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory code not found in session: " + request.inventoryCode()));

        List<CommentEvent> events = SimulationCommentGenerator.generate(
                sellerId, request.sessionId(), request.inventoryCode(), request.simulatedBuyers());

        AtomicInteger ordersCreated = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger duplicatesIgnored = new AtomicInteger();
        AtomicInteger conflictsPrevented = new AtomicInteger();
        AtomicLong totalLatencyMs = new AtomicLong();

        int poolSize = Math.min(50, request.simulatedBuyers());
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);

        List<Callable<Void>> tasks = events.stream()
                .<Callable<Void>>map(event -> () -> {
                    long start = System.currentTimeMillis();
                    try {
                        PurchaseIntent intent = purchaseIntentParser.parse(
                                event.sellerId(),
                                event.sessionId(),
                                event.platformUserId(),
                                event.rawMessage(),
                                event.sourceCreatedAt()
                        );

                        Optional<Order> order = reservationEngine.reserve(
                                event.platform(), event.platformEventId(), intent, item.getId());

                        if (order.isPresent()) {
                            ordersCreated.incrementAndGet();
                        } else {
                            duplicatesIgnored.incrementAndGet();
                        }
                    } catch (OutOfStockException e) {
                        rejected.incrementAndGet();
                        conflictsPrevented.incrementAndGet();
                    } catch (ReservationContendedException e) {
                        rejected.incrementAndGet();
                    } catch (PurchaseIntentRejectedException e) {
                        rejected.incrementAndGet();
                    } catch (Exception e) {
                        log.error("Simulation task failed unexpectedly for event {}",
                                event.platformEventId(), e);
                        rejected.incrementAndGet();
                    } finally {
                        totalLatencyMs.addAndGet(System.currentTimeMillis() - start);
                    }
                    return null;
                })
                .toList();

        List<Future<Void>> futures;
        try {
            futures = executor.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Simulation interrupted", e);
        } finally {
            executor.shutdown();
        }

        int unexpectedFailures = 0;
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                unexpectedFailures++;
                log.error("Simulation task threw an unexpected Throwable", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (unexpectedFailures > 0) {
            log.error("{} of {} simulation tasks failed with an unhandled Throwable",
                    unexpectedFailures, futures.size());
        }

        long averageLatency = events.isEmpty() ? 0 : totalLatencyMs.get() / events.size();

        return new SimulationResult(
                ordersCreated.get(),
                rejected.get(),
                duplicatesIgnored.get(),
                conflictsPrevented.get(),
                averageLatency
        );
    }
}