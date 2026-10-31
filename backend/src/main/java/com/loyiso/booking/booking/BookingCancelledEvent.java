package com.loyiso.booking.booking;

import java.time.LocalDate;
import java.time.LocalTime;

/** Published inside the cancellation transaction. */
public record BookingCancelledEvent(
        String reference,
        String branchName,
        String branchAddress,
        LocalDate date,
        LocalTime startTime,
        String customerName,
        String customerEmail) {

    public static BookingCancelledEvent from(Booking booking) {
        return new BookingCancelledEvent(
                booking.getReference(),
                booking.getBranch().getName(),
                booking.getBranch().getAddress() + ", " + booking.getBranch().getCity(),
                booking.getAppointmentDate(),
                booking.getStartTime(),
                booking.getCustomerName(),
                booking.getCustomerEmail());
    }

    @Override
    public String toString() {
        return "BookingCancelledEvent[reference=" + reference + "]";
    }
}