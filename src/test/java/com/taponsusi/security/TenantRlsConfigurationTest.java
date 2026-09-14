package com.taponsusi.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
class TenantRlsConfigurationTest {

    private static final Set<String> TENANT_TABLES = Set.of(
            "live_sessions",
            "inventory_items",
            "orders",
            "audit_events",
            "reservation_proofs",
            "buyer_reputation",
            "waitlist_entries"
    );

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void everyTenantTableHasRowLevelSecurityEnabledAndForced() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT
                    c.relname,
                    c.relrowsecurity,
                    c.relforcerowsecurity
                FROM pg_class c
                JOIN pg_namespace n
                    ON n.oid = c.relnamespace
                WHERE n.nspname = 'public'
                  AND c.relname IN (
                      'live_sessions',
                      'inventory_items',
                      'orders',
                      'audit_events',
                      'reservation_proofs',
                      'buyer_reputation',
                      'waitlist_entries'
                  )
                """)
                .getResultList();

        Map<String, RlsStatus> statusByTable = rows.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> new RlsStatus(
                                (Boolean) row[1],
                                (Boolean) row[2]
                        )
                ));

        assertThat(statusByTable.keySet())
                .containsExactlyInAnyOrderElementsOf(TENANT_TABLES);

        for (String table : TENANT_TABLES) {
            RlsStatus status = statusByTable.get(table);

            assertThat(status.enabled())
                    .as("RLS must be enabled for %s", table)
                    .isTrue();

            assertThat(status.forced())
                    .as(
                            "RLS must be forced for table owner on %s",
                            table
                    )
                    .isTrue();
        }
    }

    @Test
    @Transactional
    void everyTenantTableHasSellerIsolationPolicy() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT
                    tablename,
                    policyname,
                    qual
                FROM pg_policies
                WHERE schemaname = 'public'
                  AND tablename IN (
                      'live_sessions',
                      'inventory_items',
                      'orders',
                      'audit_events',
                      'reservation_proofs',
                      'buyer_reputation',
                      'waitlist_entries'
                  )
                """)
                .getResultList();

        Map<String, List<Object[]>> policiesByTable = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> (String) row[0]
                ));

        assertThat(policiesByTable.keySet())
                .containsExactlyInAnyOrderElementsOf(TENANT_TABLES);

        for (String table : TENANT_TABLES) {
            List<Object[]> policies = policiesByTable.get(table);

            assertThat(policies)
                    .as("%s must have an RLS policy", table)
                    .isNotEmpty();

            assertThat(policies)
                    .as(
                            "%s must use app.current_seller_id",
                            table
                    )
                    .anyMatch(row -> {
                        String expression = String.valueOf(row[2]);

                        return expression.contains("seller_id")
                                && expression.contains(
                                "app.current_seller_id"
                        );
                    });
        }
    }

    private record RlsStatus(
            boolean enabled,
            boolean forced
    ) {
    }
}