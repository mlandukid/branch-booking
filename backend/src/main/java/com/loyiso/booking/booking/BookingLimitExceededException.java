package com.loyiso.booking.booking;

public class BookingLimitExceededException extends RuntimeException {

    public BookingLimitExceededException(String message) {
        super(message);
    }
}