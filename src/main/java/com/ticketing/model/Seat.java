package com.ticketing.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "seats")
@Data
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    @JsonIgnore
    private Event event;

    @Column(nullable = false)
    private String seatRow; // "row" is a reserved word in some DBs, so seatRow is safer

    @Column(nullable = false)
    private Integer seatNumber;

    @Column(nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status = SeatStatus.AVAILABLE;

    private Long heldByUserId;

    private LocalDateTime heldUntil;
    
   

    // THE CRITICAL FIELD for concurrency control
    @Version
    private Long version;

    public enum SeatStatus {
        AVAILABLE, HELD, BOOKED
    }
}