package com.ticketing.service;

import com.razorpay.Utils;
import java.util.Map;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.ticketing.model.Booking;
import com.ticketing.model.Event;
import com.ticketing.model.Seat;
import com.ticketing.model.User;
import com.ticketing.repository.BookingRepository;
import com.ticketing.repository.EventRepository;
import com.ticketing.repository.SeatRepository;
import com.ticketing.repository.UserRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BookingService {

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    @Transactional
    public JSONObject createRazorpayOrder(Long eventId, Long userId, List<Long> seatIds) throws Exception {
        List<Seat> seats = seatRepository.findAllById(seatIds);

        // safety check: make sure every seat is actually HELD by this same user before allowing payment
        for (Seat seat : seats) {
            if (seat.getStatus() != Seat.SeatStatus.HELD || !userId.equals(seat.getHeldByUserId())) {
                throw new IllegalStateException("One or more seats are not held by you. Please reselect your seats.");
            }
        }

        BigDecimal totalAmount = seats.stream()
            .map(Seat::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Razorpay expects amount in paise (smallest currency unit), so multiply by 100
        int amountInPaise = totalAmount.multiply(BigDecimal.valueOf(100)).intValue();

        RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountInPaise);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "booking_rcpt_" + System.currentTimeMillis());

        Order order = razorpayClient.orders.create(orderRequest);

        // create a PENDING booking record now, linked to this Razorpay order, so we can confirm it later
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new RuntimeException("Event not found"));

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setEvent(event);
        booking.setSeats(seats);
        booking.setStripePaymentId(order.get("id")); // reusing this field name for now; we'll rename later
        booking.setStatus(Booking.BookingStatus.PENDING);
        bookingRepository.save(booking);

        JSONObject response = new JSONObject();
        response.put("orderId", order.get("id").toString());
        response.put("amount", amountInPaise);
        response.put("currency", "INR");
        response.put("keyId", razorpayKeyId); // frontend needs this public key id to open checkout
        response.put("bookingId", booking.getId());
        
        return response;
    }
    @Transactional
    public void confirmBooking(Long bookingId, String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) throws Exception {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new RuntimeException("Booking not found"));

        // verify the payment signature to make sure this request is genuinely from Razorpay,
        // not someone directly calling our API pretending payment succeeded
        JSONObject options = new JSONObject();
        options.put("razorpay_order_id", razorpayOrderId);
        options.put("razorpay_payment_id", razorpayPaymentId);
        options.put("razorpay_signature", razorpaySignature);

        boolean isValidSignature = Utils.verifyPaymentSignature(options, razorpayKeySecret);

        if (!isValidSignature) {
            booking.setStatus(Booking.BookingStatus.FAILED);
            bookingRepository.save(booking);
            throw new IllegalStateException("Payment verification failed.");
        }

        booking.setStatus(Booking.BookingStatus.CONFIRMED);
        booking.setStripePaymentId(razorpayPaymentId);
        bookingRepository.save(booking);

        for (Seat seat : booking.getSeats()) {
            seat.setStatus(Seat.SeatStatus.BOOKED);
            seat.setHeldByUserId(null);
            seat.setHeldUntil(null);
            seatRepository.save(seat);
        }
    }
    public List<Booking> getBookingsForUser(Long userId) {
        return bookingRepository.findByUserId(userId);
    }
}