package com.loyiso.booking.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.loyiso.booking.booking.BookingCreatedEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the email pipeline end to end: a committed booking produces exactly one confirmation,
 * a lost race produces none, and a rolled-back transaction produces none.
 * Uses slots on branch 1 in the afternoon so it never collides with other test classes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BookingEmailIntegrationTests {

    static class RecordingNotifier implements BookingNotifier {
        final List<BookingCreatedEvent> sent = new CopyOnWriteArrayList<>();

        @Override
        public void bookingConfirmed(BookingCreatedEvent event) {
            sent.add(event);
        }

        long countFor(String email) {
            return sent.stream().filter(e -> e.customerEmail().equals(email)).count();
        }
    }

    @TestConfiguration
    static class RecordingConfig {
        @Bean
        @Primary
        RecordingNotifier recordingNotifier() {
            return new RecordingNotifier();
        }
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private RecordingNotifier recorder;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void confirmedBookingSendsExactlyOneEmail() throws Exception {
        String email = "one-email@example.com";

        HttpResponse<String> response = post(json("14:00", "Thandi Nkosi", email));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(waitFor(() -> recorder.countFor(email) == 1)).isTrue();
        BookingCreatedEvent sent = recorder.sent.stream()
                .filter(e -> e.customerEmail().equals(email)).findFirst().orElseThrow();
        assertThat(sent.branchName()).isEqualTo("Sandton");
        assertThat(sent.startTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(sent.endTime()).isEqualTo(LocalTime.of(14, 30));
    }

    @Test
    void customerWhoLostTheRaceGetsNoEmail() throws Exception {
        String winner = "winner@example.com";
        String loser = "loser@example.com";

        HttpResponse<String> first = post(json("14:30", "Winner", winner));
        HttpResponse<String> second = post(json("14:30", "Loser", loser));

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(waitFor(() -> recorder.countFor(winner) == 1)).isTrue();
        Thread.sleep(1000);
        assertThat(recorder.countFor(loser)).isZero();
    }

    @Test
    void eventPublishedInACommittedTransactionIsDelivered() throws Exception {
        BookingCreatedEvent event = event("committed-ref", "committed@example.com");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> publisher.publishEvent(event));

        assertThat(waitFor(() -> recorder.countFor("committed@example.com") == 1)).isTrue();
    }

    @Test
    void eventPublishedInARolledBackTransactionIsNeverDelivered() throws Exception {
        BookingCreatedEvent event = event("rolled-back-ref", "rolled-back@example.com");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            publisher.publishEvent(event);
            status.setRollbackOnly();
        });

        Thread.sleep(1000);
        assertThat(recorder.countFor("rolled-back@example.com")).isZero();
    }

    private static BookingCreatedEvent event(String reference, String email) {
        return new BookingCreatedEvent(reference, "Sandton", "Sandton City, Johannesburg",
                LocalDate.of(2026, 11, 2), LocalTime.of(9, 0), LocalTime.of(9, 30), "Test Customer", email);
    }

    private static boolean waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return condition.getAsBoolean();
    }

    private static LocalDate weekdayAhead() {
        LocalDate date = LocalDate.now(ZoneId.of("Africa/Johannesburg")).plusDays(5);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }

    private static String json(String time, String name, String email) {
        return """
                {"branchId":1,"date":"%s","startTime":"%s","customerName":"%s","customerEmail":"%s"}
                """.formatted(weekdayAhead(), time, name, email);
    }

    private HttpResponse<String> post(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/bookings"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}