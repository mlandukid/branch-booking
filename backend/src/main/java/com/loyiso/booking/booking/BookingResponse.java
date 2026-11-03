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
        Instant createdAt,
        @Schema(description = "Private token for changing or cancelling this booking. Returned once, when the "
                + "booking is created, and also sent in the confirmation email. Null in every other response.")
        String manageToken,
        @Schema(example = "REPLACE_CARD", description = "Why the customer is visiting")
        String reason) {

    public BookingResponse(String reference, Long branchId, String branchName, LocalDate date, LocalTime startTime,
                           LocalTime endTime, String customerName, String customerEmail, Instant createdAt) {
        this(reference, branchId, branchName, date, startTime, endTime, customerName, customerEmail, createdAt, null, null);
    }

    public BookingResponse(String reference, Long branchId, String branchName, LocalDate date, LocalTime startTime,
                           LocalTime endTime, String customerName, String customerEmail, Instant createdAt,
                           String manageToken) {
        this(reference, branchId, branchName, date, startTime, endTime, customerName, customerEmail, createdAt,
                manageToken, null);
    }

    /** The full response, without a token. Used when an earlier request is replayed. */
    public static BookingResponse from(Booking booking) {
        return build(booking, booking.getCustomerEmail(), null);
    }

    /** The response to a brand new booking, the only place the private token is handed out. */
    public static BookingResponse created(Booking booking, String manageToken) {
        return build(booking, booking.getCustomerEmail(), manageToken);
    }

    /** Used when a booking is looked up or managed: the email address is masked. */
    public static BookingResponse forLookup(Booking booking) {
        return build(booking, maskEmail(booking.getCustomerEmail()), null);
    }

    /** Leaves out the token, the email address and the name, so logging a response can never leak them. */
    @Override
    public String toString() {
        return "BookingResponse[reference=" + reference + "]";
    }

    static String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static BookingResponse build(Booking booking, String email, String manageToken) {
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
                booking.getCreatedAt(),
                manageToken,
                booking.getReason().name());
    }
}