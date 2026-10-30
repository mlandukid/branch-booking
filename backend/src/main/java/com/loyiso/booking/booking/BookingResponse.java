package com.loyiso.booking.booking;

import com.loyiso.booking.branch.Branch;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "A confirmed booking")
public record BookingResponse(
        String reference,
        Long branchId,
        String branchName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String customerName,
        String customerEmail,
        Instant createdAt) {

    /** The full response, returned once to the customer who has just made the booking. */
    public static BookingResponse from(Booking booking) {
        return build(booking, booking.getCustomerEmail());
    }

    /** Used when a booking is looked up by its reference: the email address is masked. */
    public static BookingResponse forLookup(Booking booking) {
        return build(booking, maskEmail(booking.getCustomerEmail()));
    }

    static String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static BookingResponse build(Booking booking, String email) {
        Branch branch = booking.getBranch();
        return new BookingResponse(
                booking.getReference(),
                branch.getId(),
                branch.getName(),
                booking.getAppointmentDate(),
                booking.getStartTime(),
                booking.getStartTime().plusMinutes(branch.getSlotMinutes()),
                booking.getCustomerName(),
                email,
                booking.getCreatedAt());
    }
}