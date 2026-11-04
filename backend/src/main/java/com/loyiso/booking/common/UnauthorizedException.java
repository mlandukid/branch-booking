package com.loyiso.booking.common;

public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("A valid admin key is required.");
    }
}