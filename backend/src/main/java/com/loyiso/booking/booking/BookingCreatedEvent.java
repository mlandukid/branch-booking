package com.loyiso.booking.booking;

import com.loyiso.booking.branch.Branch;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Published inside the booking transaction. Listeners that must only act on committed
 * bookings (such as the confirmation email) subscribe with AFTER_COMMIT.
 */
public record BookingCreatedEvent(
        String reference,
        String branchName,
        String branchAddress,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String customerName,
        String customerEmail) {

    public static BookingCreatedEvent from(Booking booking) {
        Branch branch = booking.getBranch();
        return new BookingCreatedEvent(
                booking.getReference(),
                branch.getName(),
                branch.getAddress() + ", " + branch.getCity(),
                booking.getAppointmentDate(),
                booking.getStartTime(),
                booking.getStartTime().plusMinutes(branch.getSlotMinutes()),
                booking.getCustomerName(),
                booking.getCustomerEmail());
    }
}