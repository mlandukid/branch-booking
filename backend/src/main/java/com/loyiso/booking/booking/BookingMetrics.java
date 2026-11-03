package com.loyiso.booking.booking;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Business counters, counted only after a change has committed. Conflicts are already visible as
 * http_server_requests with status 409, so they are not counted again here.
 */
@Component
public class BookingMetrics {

    private final MeterRegistry registry;

    public BookingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(BookingCreatedEvent event) {
        String reason = event.reason() == null ? BookingReason.OTHER.name() : event.reason().name();
        registry.counter("bookings.created", "branch", event.branchName(), "reason", reason).increment();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(BookingCancelledEvent event) {
        registry.counter("bookings.cancelled", "branch", event.branchName()).increment();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRescheduled(BookingRescheduledEvent event) {
        registry.counter("bookings.rescheduled", "branch", event.branchName()).increment();
    }
}