package com.loyiso.booking.outbox;

import com.loyiso.booking.booking.BookingCancelledEvent;
import com.loyiso.booking.booking.BookingCreatedEvent;
import com.loyiso.booking.booking.BookingRescheduledEvent;
import com.loyiso.booking.notification.EmailComposer;
import com.loyiso.booking.notification.EmailContent;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Queues the email inside the booking transaction, just before it commits. If writing the email fails,
 * the booking is rolled back too, and if the booking is rolled back, the email never existed.
 */
@Component
public class EmailOutboxWriter {

    private final EmailComposer composer;
    private final OutboxEmailRepository repository;
    private final Clock clock;

    public EmailOutboxWriter(EmailComposer composer, OutboxEmailRepository repository, Clock clock) {
        this.composer = composer;
        this.repository = repository;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onBookingCreated(BookingCreatedEvent event) {
        enqueue(composer.confirmation(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onBookingCancelled(BookingCancelledEvent event) {
        enqueue(composer.cancellation(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onBookingRescheduled(BookingRescheduledEvent event) {
        enqueue(composer.rescheduled(event));
    }

    private void enqueue(EmailContent email) {
        repository.save(OutboxEmail.pending(email.to(), email.subject(), email.body(), Instant.now(clock)));
    }
}