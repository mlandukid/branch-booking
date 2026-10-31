package com.loyiso.booking.notification;

import com.loyiso.booking.booking.BookingCancelledEvent;
import com.loyiso.booking.booking.BookingCreatedEvent;
import com.loyiso.booking.booking.BookingRescheduledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Simulated delivery for when real email is switched off (booking.mail.enabled=false). */
@Component
@ConditionalOnProperty(name = "booking.mail.enabled", havingValue = "false")
public class LoggingBookingNotifier implements BookingNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingBookingNotifier.class);

    @Override
    public void bookingConfirmed(BookingCreatedEvent event) {
        log.info("Simulated confirmation for booking {} at {} on {} {}",
                event.reference(), event.branchName(), event.date(), event.startTime());
    }

    @Override
    public void bookingCancelled(BookingCancelledEvent event) {
        log.info("Simulated cancellation notice for booking {}", event.reference());
    }

    @Override
    public void bookingRescheduled(BookingRescheduledEvent event) {
        log.info("Simulated reschedule notice for booking {}, now {} {}",
                event.reference(), event.newDate(), event.newStart());
    }
}