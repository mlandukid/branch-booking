package com.loyiso.booking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Retries, replays and identical requests sent at the same moment, against the real application. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IdempotencyFlowTests {

    private static final Pattern REFERENCE = Pattern.compile("\"reference\":\"([^\"]+)\"");

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private BookingRepository bookingRepository;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void repeatingARequestWithTheSameKeyReturnsTheOriginalBooking() throws Exception {
        LocalDate date = weekdayAhead(10);
        String key = UUID.randomUUID().toString();
        String body = json(date, "15:00", "replay@example.com");

        HttpResponse<String> first = post(body, key);
        HttpResponse<String> second = post(body, key);

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(reference(second.body())).isEqualTo(reference(first.body()));
        assertThat(first.headers().firstValue("Idempotent-Replayed")).isEmpty();
        assertThat(second.headers().firstValue("Idempotent-Replayed")).contains("true");
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejected() throws Exception {
        LocalDate date = weekdayAhead(10);
        String key = UUID.randomUUID().toString();

        assertThat(post(json(date, "15:30", "reuse@example.com"), key).statusCode()).isEqualTo(201);
        HttpResponse<String> reused = post(json(date, "08:00", "reuse@example.com"), key);

        assertThat(reused.statusCode()).isEqualTo(422);
        assertThat(reused.body()).contains("Idempotency-Key");
    }

    @Test
    void aMalformedKeyIsRejected() throws Exception {
        HttpResponse<String> response = post(json(weekdayAhead(10), "09:30", "badkey@example.com"), "x");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void identicalRequestsSentAtTheSameMomentAllGetTheSameBooking() throws Exception {
        int attempts = 8;
        LocalDate date = weekdayAhead(10);
        String key = UUID.randomUUID().toString();
        String body = json(date, "08:30", "twins@example.com");

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                return post(body, key);
            }));
        }
        go.countDown();

        Set<String> references = new HashSet<>();
        int replays = 0;
        for (Future<HttpResponse<String>> future : futures) {
            HttpResponse<String> response = future.get(60, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(201);
            references.add(reference(response.body()));
            if (response.headers().firstValue("Idempotent-Replayed").isPresent()) {
                replays++;
            }
        }
        pool.shutdown();

        assertThat(references).hasSize(1);
        assertThat(replays).isEqualTo(attempts - 1);
        assertThat(bookingRepository.findBookedStartTimes(1L, date))
                .filteredOn(time -> time.equals(LocalTime.of(8, 30)))
                .hasSize(1);
    }

    private static LocalDate weekdayAhead(int minDays) {
        LocalDate date = LocalDate.now(ZoneId.of("Africa/Johannesburg")).plusDays(minDays);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }

    private static String json(LocalDate date, String time, String email) {
        return """
                {"branchId":1,"date":"%s","startTime":"%s","customerName":"Retry Tester","customerEmail":"%s"}
                """.formatted(date, time, email);
    }

    private static String reference(String body) {
        Matcher matcher = REFERENCE.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private HttpResponse<String> post(String body, String key) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/bookings"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}