package com.loyiso.booking.notification;

/** A ready-to-send email. */
public record EmailContent(String to, String subject, String body) {
}