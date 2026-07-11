package com.ticketing.repository;

import com.ticketing.model.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByEventId(Long eventId);

    List<Seat> findByStatusAndHeldUntilBefore(Seat.SeatStatus status, LocalDateTime time);
}