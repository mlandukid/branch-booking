package com.loyiso.booking.booking;

import java.time.LocalDate;
import java.time.LocalTime;

/** Published inside the reschedule transaction. */
public record BookingRescheduledEvent(
        String reference,
        String branchName,
        String branchAddress,
        LocalDate oldDate,
        LocalTime oldStart,
        LocalDate newDate,
        LocalTime newStart,
        LocalTime newEnd,
        String customerName,
        String customerEmail) {

    public static BookingRescheduledEvent from(Booking booking, LocalDate oldDate, LocalTime oldStart) {
        return new BookingRescheduledEvent(
                booking.getReference(),
                booking.getBranch().getName(),
                booking.getBranch().getAddress() + ", " + booking.getBranch().getCity(),
                oldDate,
                oldStart,
                booking.getAppointmentDate(),
                booking.getStartTime(),
                booking.getStartTime().plusMinutes(booking.getBranch().getSlotMinutes()),
                booking.getCustomerName(),
                booking.getCustomerEmail());
    }

    @Override
    public String toString() {
        return "BookingRescheduledEvent[reference=" + reference + "]";
    }
}