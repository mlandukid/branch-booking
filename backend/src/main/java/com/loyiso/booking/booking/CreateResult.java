package com.loyiso.booking.booking;

/** The outcome of a booking request: the booking, and whether it was replayed from an earlier request. */
public record CreateResult(BookingResponse booking, boolean replayed) {
}