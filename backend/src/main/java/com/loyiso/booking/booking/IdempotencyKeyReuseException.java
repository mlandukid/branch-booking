package com.loyiso.booking.booking;

public class IdempotencyKeyReuseException extends RuntimeException {

    public IdempotencyKeyReuseException(String message) {
        super(message);
    }
}