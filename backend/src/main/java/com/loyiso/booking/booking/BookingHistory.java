package com.loyiso.booking.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** An audit record of a change to a booking. It holds no personal data, only the reference and times. */
@Entity
@Table(name = "booking_history")
public class BookingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String reference;

    @Column(nullable = false, updatable = false, length = 20)
    private String action;

    @Column(nullable = false, updatable = false)
    private Long branchId;

    @Column(nullable = false, updatable = false)
    private LocalDate oldDate;

    @Column(nullable = false, updatable = false)
    private LocalTime oldTime;

    @Column(updatable = false)
    private LocalDate newDate;

    @Column(updatable = false)
    private LocalTime newTime;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected BookingHistory() {
        // required by JPA
    }

    private BookingHistory(String reference, String action, Long branchId, LocalDate oldDate, LocalTime oldTime,
                           LocalDate newDate, LocalTime newTime, Instant createdAt) {
        this.reference = reference;
        this.action = action;
        this.branchId = branchId;
        this.oldDate = oldDate;
        this.oldTime = oldTime;
        this.newDate = newDate;
        this.newTime = newTime;
        this.createdAt = createdAt;
    }

    public static BookingHistory cancelled(Booking booking, Instant at) {
        return new BookingHistory(booking.getReference(), "CANCELLED", booking.getBranch().getId(),
                booking.getAppointmentDate(), booking.getStartTime(), null, null, at);
    }

    public static BookingHistory rescheduled(Booking booking, LocalDate oldDate, LocalTime oldTime, Instant at) {
        return new BookingHistory(booking.getReference(), "RESCHEDULED", booking.getBranch().getId(),
                oldDate, oldTime, booking.getAppointmentDate(), booking.getStartTime(), at);
    }

    public String getReference() { return reference; }
    public String getAction() { return action; }
}