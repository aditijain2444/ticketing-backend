package com.ticketing.controller;

import com.ticketing.dto.ConfirmBookingRequest;
import java.util.Map;
import com.ticketing.dto.ConfirmBookingRequest;
import com.ticketing.dto.BookingRequest;
import com.ticketing.service.BookingService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@CrossOrigin(origins = "http://localhost:5173")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckout(@RequestBody BookingRequest request) {
        try {
            JSONObject order = bookingService.createRazorpayOrder(
                request.getEventId(), request.getUserId(), request.getSeatIds()
            );
            return ResponseEntity.ok(order.toMap());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    @PostMapping("/{bookingId}/confirm")
    public ResponseEntity<?> confirmBooking(@PathVariable Long bookingId, @RequestBody ConfirmBookingRequest request) {
        try {
            bookingService.confirmBooking(
                bookingId,
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()
            );
            return ResponseEntity.ok(Map.of("status", "CONFIRMED"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    @GetMapping("/my")
    public ResponseEntity<?> getMyBookings(@RequestParam Long userId) {
        return ResponseEntity.ok(bookingService.getBookingsForUser(userId));
    }
    
}