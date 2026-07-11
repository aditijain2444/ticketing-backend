package com.ticketing.service;

import com.ticketing.model.Seat;
import com.ticketing.repository.SeatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SeatService {

    @Autowired
    private SeatRepository seatRepository;

    public List<Seat> getSeatsForEvent(Long eventId) {
        return seatRepository.findByEventId(eventId);
    }

    @Transactional
    public Seat holdSeat(Long seatId, Long userId) {
        Seat seat = seatRepository.findById(seatId)
            .orElseThrow(() -> new RuntimeException("Seat not found"));

        if (seat.getStatus() != Seat.SeatStatus.AVAILABLE) {
            throw new IllegalStateException("Seat is not available");
        }

        seat.setStatus(Seat.SeatStatus.HELD);
        seat.setHeldByUserId(userId);
        seat.setHeldUntil(LocalDateTime.now().plusMinutes(10));

        try {
            return seatRepository.save(seat);
            // if another request already updated this row between our read and this save,
            // the @Version check fails here and throws OptimisticLockingFailureException
        } catch (OptimisticLockingFailureException e) {
            throw new IllegalStateException("Seat was just taken by someone else. Please pick another seat.");
        }
    }
    @Transactional
    public void releaseSeat(Long seatId, Long userId) {
        Seat seat = seatRepository.findById(seatId)
            .orElseThrow(() -> new RuntimeException("Seat not found"));

        // only release if this exact user is the one currently holding it
        // (prevents someone from releasing a seat someone else is legitimately holding)
        if (seat.getStatus() == Seat.SeatStatus.HELD && userId.equals(seat.getHeldByUserId())) {
            seat.setStatus(Seat.SeatStatus.AVAILABLE);
            seat.setHeldByUserId(null);
            seat.setHeldUntil(null);
            seatRepository.save(seat);
        }
    }
}