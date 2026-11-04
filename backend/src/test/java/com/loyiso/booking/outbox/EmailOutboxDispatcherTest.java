package com.loyiso.booking.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.loyiso.booking.notification.EmailSender;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;

class EmailOutboxDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-10-26T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Africa/Johannesburg"));

    private OutboxEmailRepository repository;
    private EmailSender sender;
    private EmailOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxEmailRepository.class);
        sender = mock(EmailSender.class);
        dispatcher = new EmailOutboxDispatcher(repository, sender, CLOCK, 20, 3, 30);
    }

    private OutboxEmail due(int attemptsSoFar) {
        OutboxEmail email = OutboxEmail.pending("thandi@example.com", "Subject", "Body", NOW);
        ReflectionTestUtils.setField(email, "id", 1L);
        ReflectionTestUtils.setField(email, "attempts", attemptsSoFar);
        when(repository.findDue(any(Instant.class), any(Pageable.class))).thenReturn(List.of(email));
        when(repository.claim(eq(1L), any(Instant.class), any(Instant.class))).thenReturn(1);
        return email;
    }

    @Test
    void sendsADueEmailAndMarksItSent() {
        due(0);

        assertThat(dispatcher.dispatchBatch()).isEqualTo(1);

        verify(sender).send("thandi@example.com", "Subject", "Body");
        verify(repository).markSent(eq(1L), any(Instant.class));
    }

    @Test
    void leavesAnEmailThatAnotherInstanceHasClaimed() {
        OutboxEmail email = due(0);
        when(repository.claim(eq(email.getId()), any(Instant.class), any(Instant.class))).thenReturn(0);

        assertThat(dispatcher.dispatchBatch()).isZero();

        verifyNoInteractions(sender);
    }

    @Test
    void aFailureIsRetriedLaterWithBackoff() {
        due(0);
        doThrow(new MailSendException("smtp down")).when(sender).send(anyString(), anyString(), anyString());

        dispatcher.dispatchBatch();

        ArgumentCaptor<Instant> next = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(repository).markFailed(eq(1L), eq("PENDING"), next.capture(), error.capture());
        assertThat(next.getValue()).isEqualTo(NOW.plusSeconds(30));
        assertThat(error.getValue()).contains("MailSendException");
    }

    @Test
    void theBackoffDoublesOnEachAttempt() {
        due(1);
        doThrow(new MailSendException("smtp down")).when(sender).send(anyString(), anyString(), anyString());

        dispatcher.dispatchBatch();

        ArgumentCaptor<Instant> next = ArgumentCaptor.forClass(Instant.class);
        verify(repository).markFailed(eq(1L), eq("PENDING"), next.capture(), anyString());
        assertThat(next.getValue()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void givesUpAfterTheLastAttempt() {
        due(2);
        doThrow(new MailSendException("smtp down")).when(sender).send(anyString(), anyString(), anyString());

        dispatcher.dispatchBatch();

        verify(repository).markFailed(eq(1L), eq("FAILED"), any(Instant.class), anyString());
    }
}