package com.loyiso.booking.notification;

import com.loyiso.booking.booking.BookingCancelledEvent;
import com.loyiso.booking.booking.BookingCreatedEvent;
import com.loyiso.booking.booking.BookingRescheduledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to a booking change only after its transaction has committed, on a separate thread.
 * A rolled-back change never reaches this listener, and a failing mail server can never
 * affect the booking itself.
 */
@Component
public class BookingNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(BookingNotificationListener.class);

    private final BookingNotifier notifier;

    public BookingNotificationListener(BookingNotifier notifier) {
        this.notifier = notifier;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCreated(BookingCreatedEvent event) {
        try {
            notifier.bookingConfirmed(event);
        } catch (Exception ex) {
            log.error("Could not send confirmation for booking {}", event.reference(), ex);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelledEvent event) {
        try {
            notifier.bookingCancelled(event);
        } catch (Exception ex) {
            log.error("Could not send cancellation notice for booking {}", event.reference(), ex);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingRescheduled(BookingRescheduledEvent event) {
        try {
            notifier.bookingRescheduled(event);
        } catch (Exception ex) {
            log.error("Could not send reschedule notice for booking {}", event.reference(), ex);
        }
    }
}