package com.taponsusi.common.idempotency;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private final ProcessedEventRepository processedEventRepository;

    public IdempotencyService(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public boolean isNewEvent(String platform, String platformEventId) {
        int rowsInserted = processedEventRepository.recordIfNew(platform, platformEventId);
        return rowsInserted > 0;
    }
}