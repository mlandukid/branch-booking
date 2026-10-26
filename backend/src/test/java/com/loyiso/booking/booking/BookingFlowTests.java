package com.loyiso.booking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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

/**
 * End-to-end booking tests against a real running server and a real database.
 * Each test uses its own branch and date so tests never interfere with each other.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BookingFlowTests {

    private static final Pattern REFERENCE = Pattern.compile("\"reference\":\"([^\"]+)\"");

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private BookingRepository bookingRepository;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void bookingCanBeCreatedAndFetchedByReference() throws Exception {
        LocalDate date = weekdayAhead(3);

        HttpResponse<String> created = post(json(1, date, "09:00", "Thandi Nkosi", "thandi@example.com"));

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("Sandton").contains("thandi@example.com");
        String reference = extractReference(created.body());
        assertThat(created.headers().firstValue("Location")).isPresent();
        assertThat(created.headers().firstValue("Location").get()).endsWith("/api/v1/bookings/" + reference);

        HttpResponse<String> fetched = get("/api/v1/bookings/" + reference);
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).contains(reference);
    }

    @Test
    void bookedSlotShowsAsUnavailableAndSecondBookingGets409() throws Exception {
        LocalDate date = weekdayAhead(3);

        HttpResponse<String> first = post(json(2, date, "09:00", "First Customer", "first@example.com"));
        HttpResponse<String> second = post(json(2, date, "09:00", "Second Customer", "second@example.com"));

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(second.body()).contains("no longer available");

        HttpResponse<String> slots = get("/api/v1/branches/2/slots?date=" + date);
        assertThat(slots.body()).contains("\"available\":false");
    }

    @Test
    void onlyOneOfManySimultaneousRequestsWinsTheSlot() throws Exception {
        int attempts = 12;
        LocalDate date = weekdayAhead(4);

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            int n = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                String body = json(3, date, "10:00", "Customer " + n, "customer" + n + "@example.com");
                return post(body).statusCode();
            }));
        }

        assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(attempts - 1);
        assertThat(bookingRepository.findBookedStartTimes(3L, date)).hasSize(1);
    }

    @Test
    void timeOffTheSlotGridReturns400() throws Exception {
        HttpResponse<String> response = post(json(1, weekdayAhead(3), "09:10", "Thandi", "thandi@example.com"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("not a valid slot");
    }

    @Test
    void invalidBodyReturns400WithFieldErrors() throws Exception {
        HttpResponse<String> response = post(json(1, weekdayAhead(3), "09:00", "", "not-an-email"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("customerName").contains("customerEmail");
    }

    @Test
    void unknownBranchReturns404() throws Exception {
        HttpResponse<String> response = post(json(9999, weekdayAhead(3), "09:00", "Thandi", "thandi@example.com"));

        assertThat(response.statusCode()).isEqualTo(404);
    }

    private static LocalDate weekdayAhead(int minDays) {
        LocalDate date = LocalDate.now(ZoneId.of("Africa/Johannesburg")).plusDays(minDays);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }

    private static String json(long branchId, LocalDate date, String time, String name, String email) {
        return """
                {"branchId":%d,"date":"%s","startTime":"%s","customerName":"%s","customerEmail":"%s"}
                """.formatted(branchId, date, time, name, email);
    }

    private static String extractReference(String body) {
        Matcher matcher = REFERENCE.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private HttpResponse<String> post(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/bookings"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}