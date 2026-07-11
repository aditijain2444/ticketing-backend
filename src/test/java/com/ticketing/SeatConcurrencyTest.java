package com.ticketing;

import com.ticketing.model.Event;
import com.ticketing.model.Seat;
import com.ticketing.repository.EventRepository;
import com.ticketing.repository.SeatRepository;
import com.ticketing.service.SeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class SeatConcurrencyTest {

    @Autowired
    private SeatService seatService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private EventRepository eventRepository;

    private Long testSeatId;

    @BeforeEach
    void setUp() {
        // create a throwaway event + seat fresh for every test run,
        // so this test never depends on your real seeded data
        Event event = new Event();
        event.setName("Concurrency Test Event");
        event.setVenue("Test Venue");
        event.setEventDate(LocalDateTime.now().plusDays(1));
        event.setDescription("Used only by automated tests");
        event = eventRepository.save(event);

        Seat seat = new Seat();
        seat.setEvent(event);
        seat.setSeatRow("Z");
        seat.setSeatNumber(1);
        seat.setPrice(new BigDecimal("999.00"));
        seat.setStatus(Seat.SeatStatus.AVAILABLE);
        seat = seatRepository.save(seat);

        testSeatId = seat.getId();
    }

    @Test
    void onlyOneRequestShouldWinWhenTwoUsersHoldTheSameSeatSimultaneously() throws InterruptedException {
        int numberOfAttempts = 10; // simulate 10 users clicking the same seat at once

        ExecutorService executor = Executors.newFixedThreadPool(numberOfAttempts);
        CountDownLatch readyLatch = new CountDownLatch(numberOfAttempts); // ensures all threads start together
        CountDownLatch startLatch = new CountDownLatch(1);                // the "go" signal
        CountDownLatch doneLatch = new CountDownLatch(numberOfAttempts);  // waits for all to finish

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfAttempts; i++) {
            final long userId = i + 1;
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await(); // all threads wait here until released together
                    seatService.holdSeat(testSeatId, userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();      // wait until all 10 threads are ready
        startLatch.countDown();  // release them all at the exact same moment
        doneLatch.await();       // wait until all 10 have finished
        executor.shutdown();

        System.out.println("Successful holds: " + successCount.get());
        System.out.println("Failed holds (correctly rejected): " + failureCount.get());

        // THE ACTUAL PROOF: exactly one should succeed, the rest should fail
        assertEquals(1, successCount.get(), "Exactly one request should successfully hold the seat");
        assertEquals(numberOfAttempts - 1, failureCount.get(), "All other requests should be rejected");

        // Double check the DB itself: seat should be HELD by exactly one user
        Seat finalSeat = seatRepository.findById(testSeatId).orElseThrow();
        assertEquals(Seat.SeatStatus.HELD, finalSeat.getStatus());
    }
}