# TaponSusi

TaponSusi is an in-progress tool for livestream sellers to track purchase claims, reserve limited stock, and review reservation outcomes. Its backend currently demonstrates this workflow with simulated buyer comments while the seller dashboard is being built.

## Current prototype

The simulation generates buyer comments for an item in a livestream session. The backend interprets purchase requests, checks available stock, and records the outcome. A successful reservation creates an order; an eligible buyer can enter the waitlist when stock runs out. Duplicate events are handled so the same comment does not create another order.

This is a local backend prototype. Its Facebook adapter has not yet been validated against a live Page.

## Local verification

The repository includes an automated test that sends **500 simulated buyer events** to the reservation engine using ten workers. With one unit of stock, it checks that exactly one order is created and the remaining stock is zero. It also checks that stock does not become negative. The test exercises the engine directly; it does not send 500 HTTP requests.

To run the test, install Java 21, Maven, and PostgreSQL. Create a local database named `taponsusi` at `localhost:5432` and configure `DB_USERNAME` and `DB_PASSWORD` for your database. Then run from the repository root:

```powershell
mvn -Dtest=ConcurrencyTest test
```

The test writes records to the configured database, so use a development database.

## Technologies used so far

| Technology | How it is used |
| --- | --- |
| Java 21 and Spring Boot 3.3.4 | Backend API and reservation workflow |
| PostgreSQL | Inventory, orders, and coordination of competing reservations |
| JUnit and Spring MockMvc | Automated reservation and API tests |

## What I'm learning

- How a buyer request moves through backend logic and becomes a recorded reservation outcome.
- How to test limited stock by checking orders and remaining inventory when buyer events compete for the same item.

## Next steps

- Build a seller dashboard for setting up livestream sessions, viewing inventory, and reviewing orders and waitlist outcomes.
- Add seller-facing metrics and an end-to-end walkthrough of the dashboard and backend working together.
