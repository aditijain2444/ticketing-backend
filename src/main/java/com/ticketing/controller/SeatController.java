package com.ticketing.controller;

import com.ticketing.dto.HoldRequest;
import com.ticketing.model.Seat;
import com.ticketing.service.SeatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events/{eventId}/seats")
@CrossOrigin(origins = "http://localhost:5173")
public class SeatController {

    @Autowired
    private SeatService seatService;

    @GetMapping
    public List<Seat> getSeats(@PathVariable Long eventId) {
        return seatService.getSeatsForEvent(eventId);
    }

    @PostMapping("/{seatId}/hold")
    public ResponseEntity<Seat> holdSeat(@PathVariable Long eventId, @PathVariable Long seatId, @RequestBody HoldRequest request) {
        Seat updatedSeat = seatService.holdSeat(seatId, request.getUserId());
        return ResponseEntity.ok(updatedSeat);
    }
    @PostMapping("/{seatId}/release")
    public ResponseEntity<Void> releaseSeat(@PathVariable Long eventId, @PathVariable Long seatId, @RequestBody HoldRequest request) {
        seatService.releaseSeat(seatId, request.getUserId());
        return ResponseEntity.ok().build();
    }
}