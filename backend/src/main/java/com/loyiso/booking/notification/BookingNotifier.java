package com.loyiso.booking.notification;

import com.loyiso.booking.booking.BookingCreatedEvent;

/** Delivers a booking confirmation to the customer. Implementations may throw on failure. */
public interface BookingNotifier {

    void bookingConfirmed(BookingCreatedEvent event);
}