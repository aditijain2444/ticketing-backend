package com.ticketing.service;

import com.ticketing.model.Seat;
import com.ticketing.repository.SeatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SeatExpiryService {

    @Autowired
    private SeatRepository seatRepository;

    // runs every 30 seconds
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void releaseExpiredHolds() {
        List<Seat> expiredSeats = seatRepository.findByStatusAndHeldUntilBefore(
            Seat.SeatStatus.HELD, LocalDateTime.now()
        );

        for (Seat seat : expiredSeats) {
            seat.setStatus(Seat.SeatStatus.AVAILABLE);
            seat.setHeldByUserId(null);
            seat.setHeldUntil(null);
            seatRepository.save(seat);
        }

        if (!expiredSeats.isEmpty()) {
            System.out.println("Released " + expiredSeats.size() + " expired seat hold(s).");
        }
    }
}