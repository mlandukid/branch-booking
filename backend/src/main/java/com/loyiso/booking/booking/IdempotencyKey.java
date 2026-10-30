package com.loyiso.booking.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import org.springframework.data.domain.Persistable;

/**
 * Links a client-supplied key to the booking it created. The key is the primary key, so two
 * different bookings can never claim the same key. It is Persistable so that saving always
 * issues an INSERT: a merge could silently re-point an existing key at a different booking.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey implements Persistable<String> {

    @Id
    @Column(name = "key_value", nullable = false, updatable = false, length = 64)
    private String keyValue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean fresh = true;

    protected IdempotencyKey() {
        // required by JPA
    }

    public IdempotencyKey(String keyValue, Booking booking, Instant createdAt) {
        this.keyValue = keyValue;
        this.booking = booking;
        this.createdAt = createdAt;
    }

    @Override
    public String getId() {
        return keyValue;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        fresh = false;
    }

    public Booking getBooking() {
        return booking;
    }
}