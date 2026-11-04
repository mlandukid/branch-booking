package com.loyiso.booking.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class OutboxMetricsTest {

    @Test
    void reportsHowManyEmailsAreInEachState() {
        OutboxEmailRepository repository = mock(OutboxEmailRepository.class);
        when(repository.countByStatus("PENDING")).thenReturn(3L);
        when(repository.countByStatus("FAILED")).thenReturn(1L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new OutboxMetrics(registry, repository);

        assertThat(registry.get("outbox.emails").tag("status", "pending").gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("outbox.emails").tag("status", "failed").gauge().value()).isEqualTo(1.0);
    }
}