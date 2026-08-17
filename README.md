# TaponSusi

### A backend engine that decides, provably and without overselling, who actually wins a livestream reservation.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)
![Database](https://img.shields.io/badge/PostgreSQL-locking%20%26%20RLS-blue)
![Status](https://img.shields.io/badge/status-in%20development-yellow)
![Concurrency](https://img.shields.io/badge/overselling-0%25%20verified-brightgreen)
![License](https://img.shields.io/badge/license-TBD-lightgrey)

---

## The Problem

Philippine livestream selling (Facebook Live, TikTok Live) runs entirely on sellers manually reading comments and typing "Mine!" replies in real time. At scale, this breaks in specific, well-documented ways:

- **Facebook's "Most Relevant" comment filter hides real buyer comments from the seller's own screen** — a buyer can genuinely comment first and never appear in the seller's feed at all, because Meta's frontend UI silently filters what displays.
- **"Joy miners"** spam "Mine!" across many items to secure them, then ignore the invoice entirely — sellers lose hours after every stream manually chasing ghosted buyers and releasing dead stock.
- **Network latency varies wildly** between buyers (Globe vs. Smart vs. fiber), so two buyers can each genuinely believe they commented first — with no way to settle the dispute.
- **Buyers delete their own winning comment** to dodge payment while the item stays locked, silently killing sellable inventory for the rest of the reservation window.
- **Meta rate-limits/flags Pages that PM many buyers with invoice links in a short window as spam**, freezing a seller's messaging for 24–48 hours at the exact moment they need it most.
- **Identity spoofing:** scammers clone a winning buyer's name and photo, then DM the seller first claiming the win, before the real buyer does.

None of these are solved by more UI polish. They're solved by moving the source of truth off the visible comment feed and into a backend that processes every event, in order, provably.

## What TaponSusi Actually Contributes

This is not a storefront — there's no product browsing, checkout, or payment processing. The entire contribution is a correctness-under-concurrency problem, solved deliberately:

- **Raw webhook ingestion bypasses Meta's "Most Relevant" filter entirely.** Every comment is processed from the backend Graph API payload, not the seller's rendered screen — "don't trust your screen, trust the queue."
- **PostgreSQL pessimistic row locking (`SELECT ... FOR UPDATE`)** guarantees exactly one deterministic winner per unit of inventory, with correctness prioritized over raw throughput.
- **Database-enforced idempotency** (`UNIQUE` constraint + `ON CONFLICT`) makes Meta's webhook retries (guaranteed for up to 36 hours) harmless by construction, not by hope.
- **Platform-independent core:** the Reservation Engine only ever sees a generic `CommentEvent` — it has no idea whether it came from Facebook, TikTok, or the built-in Simulation Engine.
- **Row-Level Security (Postgres, database-enforced)** guarantees strict multi-tenant isolation independent of application code correctness.
- **Hash-chained Reservation Proofs** give sellers a tamper-evident, cryptographic record of *server lock-acquisition order* for every reservation.
- **Append-only audit trail** records every reservation outcome (created, rejected, expired, cancelled) with full context.
- **Buyer reputation tracking**, scoped per-seller, with a `BLOCKED` flag that rejects a buyer's purchase intent before it ever reaches the Reservation Engine.

## Objectives

1. Zero overselling and zero duplicate reservations, even under simulated flash-sale-level contention. **Verified: 500 concurrent simulated buyers against 1 unit of stock → exactly 1 order created, reproducibly.**
2. Deterministic, explainable reservation ordering — every decision reconstructable after the fact.
3. Complete, immutable audit trail and reservation proof for every successful reservation. *(Phase 3)*
4. Strict multi-tenant isolation, enforced at the database layer (Row-Level Security), not just application code. **Verified via automated cross-tenant test.**
5. A simulation engine that reproduces production-level contention without needing a live platform connection. **Built and verified.**

## Tech Stack

Java 21 · Spring Boot 3.3.4 · PostgreSQL 18 (pessimistic locking, Row-Level Security) · Flyway · Spring Security + JWT · BCrypt

## Getting Started

```bash
# requirements: Java 21, PostgreSQL 15+, Maven (or use the bundled wrapper / your IDE's Maven)
git clone <repo-url>
cd taponsusi
cp .env.example .env        # configure DB credentials, JWT secret, Facebook App Secret
# run via your IDE (Application.java) or ./mvnw spring-boot:run
```

## Example: Simulated Flash Sale

```bash
curl -X POST http://localhost:8080/api/v1/simulation/run \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"sessionId": 1, "inventoryCode": "SWERTE", "simulatedBuyers": 500}'
```

Actual verified response shape:

```json
{
  "success": true,
  "data": {
    "ordersCreated": 1,
    "reservationsRejected": 499,
    "duplicateEventsIgnored": 0,
    "inventoryConflictsPrevented": 499,
    "averageLatencyMs": 187
  }
}
```

## Project Status

Tracked against the phased roadmap.
- [x] Phase 1 — Foundation (auth, multi-tenant core, Row-Level Security) — verified via automated cross-tenant isolation test
- [x] Phase 2 — Reservation Engine core (locking, parsing, simulation, Facebook adapter)
    - App Review not yet submitted — pending real Dev Mode validation against a live Facebook Page
- [x] Phase 3 — Reliability (proof chain, audit trail, expiration, buyer reputation, concurrency test suite)
- [ ] Phase 4 — Dashboard, invoice-on-demand, proof tokens, metrics, final write-up

## Explicitly Out of Scope

Payment processing, shipping/logistics, customer accounts, recommendations, microservice split, Kafka/RabbitMQ, a real TikTok integration, and an ordered runner-up/waitlist queue. 

## Author

A personal project exploring correctness-under-concurrency problems in backend systems, built independently. 

## License

_TBD_