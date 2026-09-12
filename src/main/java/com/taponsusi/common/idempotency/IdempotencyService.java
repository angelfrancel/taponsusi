package com.taponsusi.common.idempotency;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Supplier;


@Service
public class IdempotencyService {

    private final ProcessedEventRepository processedEventRepository;

    public IdempotencyService(
            ProcessedEventRepository processedEventRepository
    ) {
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public <T> Optional<T> executeIfNew(
            String platform,
            String platformEventId,
            Supplier<T> operation
    ) {
        int rowsInserted = processedEventRepository.recordIfNew(
                platform,
                platformEventId
        );

        if (rowsInserted == 0) {
            return Optional.empty();
        }

        return Optional.of(operation.get());
    }
}