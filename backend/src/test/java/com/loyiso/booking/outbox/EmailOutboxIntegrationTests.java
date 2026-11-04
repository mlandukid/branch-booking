package com.loyiso.booking.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.loyiso.booking.booking.BookingCreatedEvent;
import com.loyiso.booking.notification.EmailSender;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 * The outbox end to end. This class has its own in-memory database, so the dispatchers of other
 * test contexts cannot pick up its emails.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:outboxtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class EmailOutboxIntegrationTests {

    static class FlakySender implements EmailSender {
        final List<String> delivered = new CopyOnWriteArrayList<>();
        final Map<String, AtomicInteger> failuresLeft = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

        @Override
        public void send(String to, String subject, String body) {
            attempts.computeIfAbsent(to, key -> new AtomicInteger()).incrementAndGet();
            AtomicInteger left = failuresLeft.get(to);
            if (left != null && left.getAndDecrement() > 0) {
                throw new IllegalStateException("simulated mail outage");
            }
            delivered.add(to);
        }

        long deliveredTo(String address) {
            return delivered.stream().filter(address::equals).count();
        }
    }

    @TestConfiguration
    static class SenderConfig {
        @Bean
        @Primary
        FlakySender flakySender() {
            return new FlakySender();
        }
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private FlakySender sender;

    @Autowired
    private OutboxEmailRepository repository;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aBookingProducesExactlyOneDeliveredEmailAndTheBodyIsErasedAfterwards() throws Exception {
        String address = "outbox-one@example.com";

        assertThat(book("09:00", address).statusCode()).isEqualTo(201);

        assertThat(waitFor(() -> sender.deliveredTo(address) == 1)).isTrue();
        Thread.sleep(1000);
        assertThat(sender.deliveredTo(address)).isEqualTo(1);
        OutboxEmail row = repository.findAll().stream().filter(e -> e.getToAddress().equals(address)).findFirst().orElseThrow();
        assertThat(row.getStatus()).isEqualTo("SENT");
        assertThat(row.getBody()).isEmpty();
    }

    @Test
    void aMailServerThatIsDownIsRetriedUntilItRecovers() throws Exception {
        String address = "outbox-flaky@example.com";
        sender.failuresLeft.put(address, new AtomicInteger(2));

        assertThat(book("09:30", address).statusCode()).isEqualTo(201);

        assertThat(waitFor(() -> sender.deliveredTo(address) == 1)).isTrue();
        assertThat(sender.attempts.get(address).get()).isEqualTo(3);
    }

    @Test
    void aMailServerThatNeverRecoversLeavesTheEmailMarkedAsFailed() throws Exception {
        String address = "outbox-dead@example.com";
        sender.failuresLeft.put(address, new AtomicInteger(100));

        assertThat(book("10:00", address).statusCode()).isEqualTo(201);

        assertThat(waitFor(() -> repository.findAll().stream()
                .anyMatch(e -> e.getToAddress().equals(address) && e.getStatus().equals("FAILED")))).isTrue();
        assertThat(sender.deliveredTo(address)).isZero();
        assertThat(sender.attempts.get(address).get()).isEqualTo(3);
    }

    @Test
    void anEventInACommittedTransactionIsQueuedAndDelivered() throws Exception {
        String address = "outbox-committed@example.com";

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> publisher.publishEvent(event(address)));

        assertThat(waitFor(() -> sender.deliveredTo(address) == 1)).isTrue();
    }

    @Test
    void anEventInARolledBackTransactionLeavesNoEmailBehind() throws Exception {
        String address = "outbox-rolledback@example.com";

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            publisher.publishEvent(event(address));
            status.setRollbackOnly();
        });

        Thread.sleep(1500);
        assertThat(repository.findAll().stream().anyMatch(e -> e.getToAddress().equals(address))).isFalse();
        assertThat(sender.deliveredTo(address)).isZero();
    }

    private static BookingCreatedEvent event(String address) {
        return new BookingCreatedEvent("ref-" + address, "Sandton", "Sandton City, Johannesburg",
                LocalDate.of(2026, 11, 2), LocalTime.of(9, 0), LocalTime.of(9, 30), "Test Customer", address);
    }

    private HttpResponse<String> book(String time, String email) throws Exception {
        String body = """
                {"branchId":1,"date":"%s","startTime":"%s","customerName":"Outbox Tester","customerEmail":"%s"}
                """.formatted(weekdayAhead(3), time, email);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/bookings"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static LocalDate weekdayAhead(int minDays) {
        LocalDate day = LocalDate.now(ZoneId.of("Africa/Johannesburg")).plusDays(minDays);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }
        return day;
    }

    private static boolean waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return condition.getAsBoolean();
    }
}