package com.loyiso.booking.notification;

import com.loyiso.booking.booking.BookingCancelledEvent;
import com.loyiso.booking.booking.BookingCreatedEvent;
import com.loyiso.booking.booking.BookingRescheduledEvent;

/** Delivers messages about a booking to the customer. Implementations may throw on failure. */
public interface BookingNotifier {

    void bookingConfirmed(BookingCreatedEvent event);

    default void bookingCancelled(BookingCancelledEvent event) {
    }

    default void bookingRescheduled(BookingRescheduledEvent event) {
    }
}