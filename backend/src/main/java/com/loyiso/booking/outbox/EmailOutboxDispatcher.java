package com.loyiso.booking.outbox;

import com.loyiso.booking.notification.EmailSender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends queued emails. Each row is claimed with an atomic update, so several instances can run this
 * at once without sending the same email twice. A failure is retried with exponential backoff, and
 * after the last attempt the row is kept as FAILED for investigation. A row that was claimed by an
 * instance that crashed becomes available again when its claim runs out.
 */
@Component
public class EmailOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxDispatcher.class);
    private static final Duration CLAIM_TIME = Duration.ofMinutes(2);
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private final OutboxEmailRepository repository;
    private final EmailSender sender;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;
    private final long backoffSeconds;

    public EmailOutboxDispatcher(OutboxEmailRepository repository,
                                 EmailSender sender,
                                 Clock clock,
                                 @Value("${booking.outbox.batch-size:20}") int batchSize,
                                 @Value("${booking.outbox.max-attempts:8}") int maxAttempts,
                                 @Value("${booking.outbox.backoff-seconds:30}") long backoffSeconds) {
        this.repository = repository;
        this.sender = sender;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.backoffSeconds = backoffSeconds;
    }

    @Scheduled(fixedDelayString = "${booking.outbox.poll-ms:1000}")
    public void poll() {
        try {
            dispatchBatch();
        } catch (Exception ex) {
            log.error("The email dispatcher failed", ex);
        }
    }

    /** Sends everything that is due. Returns how many emails were handled. */
    public int dispatchBatch() {
        Instant now = Instant.now(clock);
        List<OutboxEmail> due = repository.findDue(now, PageRequest.of(0, batchSize));
        int handled = 0;
        for (OutboxEmail email : due) {
            if (repository.claim(email.getId(), now, now.plus(CLAIM_TIME)) == 0) {
                continue;
            }
            deliver(email, email.getAttempts() + 1);
            handled++;
        }
        return handled;
    }

    private void deliver(OutboxEmail email, int attempt) {
        try {
            sender.send(email.getToAddress(), email.getSubject(), email.getBody());
            repository.markSent(email.getId(), Instant.now(clock));
            log.info("Email {} sent after {} attempt(s)", email.getId(), attempt);
        } catch (Exception ex) {
            boolean giveUp = attempt >= maxAttempts;
            Instant next = Instant.now(clock).plus(backoff(attempt));
            repository.markFailed(email.getId(), giveUp ? "FAILED" : "PENDING", next, describe(ex));
            if (giveUp) {
                log.error("Email {} failed for good after {} attempts", email.getId(), attempt, ex);
            } else {
                log.warn("Email {} failed on attempt {}, retrying later: {}", email.getId(), attempt,
                        ex.getClass().getSimpleName());
            }
        }
    }

    private Duration backoff(int attempt) {
        Duration delay = Duration.ofSeconds(backoffSeconds).multipliedBy(1L << Math.min(attempt - 1, 10));
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    private static String describe(Exception ex) {
        String text = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}