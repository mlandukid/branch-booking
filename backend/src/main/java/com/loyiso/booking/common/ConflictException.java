package com.loyiso.booking.common;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}