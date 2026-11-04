package com.loyiso.booking.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** How many emails are waiting or have failed for good. The failed count is the one to alert on. */
@Component
public class OutboxMetrics {

    public OutboxMetrics(MeterRegistry registry, OutboxEmailRepository repository) {
        for (String status : new String[] {"PENDING", "SENT", "FAILED"}) {
            Gauge.builder("outbox.emails", repository, repo -> repo.countByStatus(status))
                    .tag("status", status.toLowerCase())
                    .register(registry);
        }
    }
}