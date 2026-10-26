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

    public static BookingResponse from(Booking booking) {
        Branch branch = booking.getBranch();
        return new BookingResponse(
                booking.getReference(),
                branch.getId(),
                branch.getName(),
                booking.getAppointmentDate(),
                booking.getStartTime(),
                booking.getStartTime().plusMinutes(branch.getSlotMinutes()),
                booking.getCustomerName(),
                booking.getCustomerEmail(),
                booking.getCreatedAt());
    }
}