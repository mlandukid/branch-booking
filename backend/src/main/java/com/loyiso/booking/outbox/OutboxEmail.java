package com.loyiso.booking.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** An email waiting to be sent, written in the same transaction as the booking change that caused it. */
@Entity
@Table(name = "email_outbox")
public class OutboxEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "to_address", nullable = false, updatable = false, length = 254)
    private String toAddress;

    @Column(nullable = false, updatable = false, length = 200)
    private String subject;

    /** Erased once the email has been delivered, because it can contain a private link. */
    @Column(nullable = false, length = 4000)
    private String body;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    private Instant claimedUntil;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant sentAt;

    @Column(length = 500)
    private String lastError;

    protected OutboxEmail() {
        // required by JPA
    }

    public static OutboxEmail pending(String toAddress, String subject, String body, Instant now) {
        OutboxEmail email = new OutboxEmail();
        email.toAddress = toAddress;
        email.subject = subject;
        email.body = body;
        email.status = "PENDING";
        email.attempts = 0;
        email.nextAttemptAt = now;
        email.createdAt = now;
        return email;
    }

    public Long getId() { return id; }
    public String getToAddress() { return toAddress; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
}