package com.taponsusi.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO processed_events (platform, platform_event_id)
            VALUES (:platform, :platformEventId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int recordIfNew(@Param("platform") String platform, @Param("platformEventId") String platformEventId);
}