package com.loyiso.booking.notification;

/** Delivers one email. Implementations throw when delivery fails, so the outbox can retry. */
public interface EmailSender {

    void send(String to, String subject, String body);
}