# Event Ticketing Platform

A full-stack event ticketing system (similar to BookMyShow/Ticketmaster) where users browse events, select seats from a live seat map, hold them temporarily, and pay via Razorpay — with race-condition-safe seat booking under concurrent load.

## Live Demo

- **App**: https://ticketing-frontend-alpha.vercel.app
- **Backend API**: https://ticketing-backend-1-2u57.onrender.com/api/events

> Note: the backend is hosted on Render's free tier, which spins down after ~15 minutes of inactivity. The first request may take 30–50 seconds to wake it back up — this is expected, not a bug.


## Why this project

Most CRUD portfolio projects don't touch the hard part of real ticketing systems: **what happens when two people click the same seat at the same time?** This project solves that with database-level optimistic locking, backed by an automated concurrency test that proves it.

## Core Flow

1. Browse events → view a live seat map
2. Click an available seat → it's held for you for 10 minutes
3. Proceed to checkout → pay via Razorpay (test mode)
4. Payment succeeds → seat is permanently booked
5. Payment fails, is cancelled, or the 10-minute hold expires → seat automatically releases back to available (via a scheduled background job)

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17+, Spring Boot, Spring Data JPA, Spring Security |
| Frontend | React (Vite), React Router, Axios |
| Database | PostgreSQL |
| Auth | JWT (stateless, BCrypt-hashed passwords) |
| Payments | Razorpay (test mode) |
| Concurrency Control | Optimistic locking (`@Version`) + scheduled expiry job |

## The Concurrency Problem — and How It's Solved

When multiple users try to hold the same seat simultaneously, only one should succeed. This is handled with **optimistic locking** rather than pessimistic (row-level) locking:

- Every `Seat` row has a `@Version` column.
- On every hold attempt, Hibernate issues `UPDATE seats SET ... WHERE id = ? AND version = ?`.
- If two requests race, the first to commit increments the version. The second request's `WHERE version = ?` clause now matches zero rows — Hibernate throws `OptimisticLockingFailureException`, which is caught and converted into a clean `409 Conflict` response.

**Why optimistic over pessimistic locking:** pessimistic locking (`SELECT ... FOR UPDATE`) would block competing transactions until the first one finishes, which hurts throughput under high contention (e.g., a popular concert on sale). Optimistic locking assumes conflicts are rare relative to total traffic, avoids holding any lock while nothing's happening, and only pays a cost at the moment of an actual conflict. The tradeoff: it doesn't scale cleanly across multiple backend instances without a shared source of truth — a natural next step would be a Redis-based distributed lock if this needed to run on more than one server.

### Automated Proof

`SeatConcurrencyTest.java` spins up 10 threads (simulating 10 different users), uses `CountDownLatch` to force all 10 to call the hold endpoint at the exact same instant, and asserts:
- Exactly 1 succeeds
- The other 9 are correctly rejected
- The database's final state shows the seat held by exactly one user

```
Successful holds: 1
Failed holds (correctly rejected): 9
```

This is a repeatable, automated test — not a manual click-through — proving the system is safe under real concurrent load.

## Auto-Release of Expired Holds

A held seat that's never paid for shouldn't stay locked forever. A `@Scheduled` job runs every 30 seconds, finds all seats where `status = HELD` and `heldUntil` has passed, and releases them back to `AVAILABLE`.

## Security

- Passwords are hashed with BCrypt — never stored or compared in plain text.
- Authentication uses stateless JWT tokens (`Authorization: Bearer <token>`), verified on every request via a custom `OncePerRequestFilter`.
- Public endpoints (browsing events/seats, register, login) are open; all seat-holding, checkout, and booking endpoints require a valid token.
- Payment confirmation is verified server-side via Razorpay's signature verification (`Utils.verifyPaymentSignature`) — a client can't fake a successful payment by simply calling the confirm endpoint with fabricated data.
- Booking amounts are always calculated server-side from the database, never trusted from the client.

## Project Structure

```
ticketing-backend/
├── src/main/java/com/ticketing/
│   ├── model/           # Seat, Event, Booking, User entities
│   ├── repository/      # Spring Data JPA repositories
│   ├── service/         # Business logic (SeatService, BookingService, AuthService, SeatExpiryService)
│   ├── controller/      # REST endpoints
│   ├── config/          # SecurityConfig, JwtUtil, JwtAuthFilter
│   ├── dto/              # Request/response objects
│   └── exception/       # Global exception handling
└── src/test/java/com/ticketing/
    └── SeatConcurrencyTest.java   # The core proof-of-concept test

ticketing-frontend/
├── src/
│   ├── pages/            # EventsList, SeatMap, Login, Register
│   ├── context/          # AuthContext (JWT session management)
│   └── api/               # Axios instance with auto-attached auth header
```

## Running Locally

### Prerequisites
- Java 17+
- Node.js
- PostgreSQL

### Backend
```bash
# create the database
createdb ticketing_db

# configure src/main/resources/application.properties with your DB credentials and Razorpay test keys

./mvnw spring-boot:run
```

### Frontend
```bash
cd ticketing-frontend
npm install
npm run dev
```

Visit `http://localhost:5173`.

## Running the Concurrency Test

```bash
./mvnw test -Dtest=SeatConcurrencyTest
```

## Known Limitations / Next Steps

- Optimistic locking works well for a single backend instance; scaling to multiple instances would need a distributed lock (Redis) or a message-queue-based reservation system.
- Real-time seat status updates currently rely on 5-second polling from the frontend; a production version would use WebSockets to push seat status changes instantly.
- No dedicated "My Bookings" page yet — bookings are stored and confirmed correctly, but not yet surfaced in a dedicated UI view.
- Payment cancellation currently relies on the 10-minute natural expiry rather than immediately releasing the seat on cancel.

## What This Project Demonstrates

- Solving a genuine concurrency/race-condition problem, not just CRUD
- A concrete, defensible architectural tradeoff (optimistic vs. pessimistic locking) with reasoning for when you'd switch
- End-to-end ownership: schema design, transactions, REST API design, third-party payment integration with signature verification, JWT-based auth, and a React frontend consuming all of it
- Writing an automated test to prove a non-obvious correctness property, rather than relying on manual testing
