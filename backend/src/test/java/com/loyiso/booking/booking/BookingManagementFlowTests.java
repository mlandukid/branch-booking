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
import java.util.Set;
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

/** Moving and cancelling through the real application, including simultaneous changes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BookingManagementFlowTests {

    private static final Pattern REFERENCE = Pattern.compile("\"reference\":\"([^\"]+)\"");
    private static final Pattern TOKEN = Pattern.compile("\"manageToken\":\"([^\"]+)\"");

    private record Created(String reference, String token) {
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private BookingRepository bookingRepository;

    private final HttpClient http = HttpClient.newHttpClient();
    private final LocalDate date = weekdayAhead(14);

    @Test
    void movingABookingFreesTheOldSlot() throws Exception {
        Created mine = create("08:00", "mover@example.com");

        HttpResponse<String> moved = patch(mine, mine.token(), "09:30");

        assertThat(moved.statusCode()).isEqualTo(200);
        assertThat(moved.body()).contains("09:30").doesNotContain("mover@example.com");
        assertThat(createStatus("08:00", "taker@example.com")).isEqualTo(201);
    }

    @Test
    void movingToATakenSlotIsRefusedAndTheBookingStaysPut() throws Exception {
        Created first = create("14:00", "first-mover@example.com");
        create("14:30", "second-mover@example.com");

        HttpResponse<String> clash = patch(first, first.token(), "14:30");

        assertThat(clash.statusCode()).isEqualTo(409);
        assertThat(get(first, first.token()).body()).contains("14:00");
    }

    @Test
    void cancellingFreesTheSlotAndTheBookingIsGone() throws Exception {
        Created mine = create("10:00", "canceller@example.com");

        assertThat(delete(mine, mine.token()).statusCode()).isEqualTo(204);

        assertThat(get(mine, mine.token()).statusCode()).isEqualTo(404);
        assertThat(delete(mine, mine.token()).statusCode()).isEqualTo(404);
        assertThat(createStatus("10:00", "rebooker@example.com")).isEqualTo(201);
    }

    @Test
    void aWrongOrMissingTokenLooksLikeAnUnknownBooking() throws Exception {
        Created mine = create("11:00", "guarded@example.com");

        HttpResponse<String> wrong = delete(mine, "not-the-token");
        HttpResponse<String> missing = delete(mine, null);
        HttpResponse<String> wrongMove = patch(mine, "not-the-token", "11:30");

        assertThat(wrong.statusCode()).isEqualTo(404);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(wrongMove.statusCode()).isEqualTo(404);
        assertThat(wrong.body()).contains("Booking not found");
        assertThat(get(mine, mine.token()).statusCode()).isEqualTo(200);
    }

    @Test
    void twoCancellationsAtTheSameMomentCancelOnlyOnce() throws Exception {
        Created mine = create("10:30", "double-cancel@example.com");
        int attempts = 4;

        List<Integer> statuses = runTogether(attempts, () -> delete(mine, mine.token()).statusCode());

        assertThat(statuses.stream().filter(status -> status == 204).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(status -> status != 204)).allMatch(status -> status == 404 || status == 409);
        assertThat(get(mine, mine.token()).statusCode()).isEqualTo(404);
    }

    @Test
    void twoMovesAtTheSameMomentNeverDuplicateOrLoseTheBooking() throws Exception {
        Created mine = create("12:00", "double-move@example.com");
        String[] targets = {"12:30", "13:00"};
        int[] next = {0};

        List<Integer> statuses = runTogether(2, () -> {
            String target;
            synchronized (next) {
                target = targets[next[0]++];
            }
            return patch(mine, mine.token(), target).statusCode();
        });

        assertThat(statuses).allMatch(status -> status == 200 || status == 409);
        assertThat(statuses).contains(200);
        Set<java.time.LocalTime> candidates = Set.of(
                java.time.LocalTime.of(12, 0), java.time.LocalTime.of(12, 30), java.time.LocalTime.of(13, 0));
        assertThat(bookingRepository.findBookedStartTimes(1L, date).stream().filter(candidates::contains)).hasSize(1);
    }

    private List<Integer> runTogether(int attempts, java.util.concurrent.Callable<Integer> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                return action.call();
            }));
        }
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return statuses;
    }

    private Created create(String time, String email) throws Exception {
        HttpResponse<String> response = post(time, email);
        assertThat(response.statusCode()).isEqualTo(201);
        Matcher reference = REFERENCE.matcher(response.body());
        Matcher token = TOKEN.matcher(response.body());
        assertThat(reference.find()).isTrue();
        assertThat(token.find()).isTrue();
        return new Created(reference.group(1), token.group(1));
    }

    private int createStatus(String time, String email) throws Exception {
        return post(time, email).statusCode();
    }

    private HttpResponse<String> post(String time, String email) throws Exception {
        String body = """
                {"branchId":1,"date":"%s","startTime":"%s","customerName":"Manage Tester","customerEmail":"%s"}
                """.formatted(date, time, email);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/v1/bookings")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(Created booking, String token) throws Exception {
        return http.send(withToken(HttpRequest.newBuilder(URI.create(url("/api/v1/bookings/" + booking.reference() + "/manage"))), token)
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(Created booking, String token) throws Exception {
        return http.send(withToken(HttpRequest.newBuilder(URI.create(url("/api/v1/bookings/" + booking.reference()))), token)
                .DELETE().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patch(Created booking, String token, String time) throws Exception {
        String body = """
                {"date":"%s","startTime":"%s"}
                """.formatted(date, time);
        return http.send(withToken(HttpRequest.newBuilder(URI.create(url("/api/v1/bookings/" + booking.reference()))), token)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest.Builder withToken(HttpRequest.Builder builder, String token) {
        return token == null ? builder : builder.header("X-Manage-Token", token);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static LocalDate weekdayAhead(int minDays) {
        LocalDate day = LocalDate.now(ZoneId.of("Africa/Johannesburg")).plusDays(minDays);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }
        return day;
    }
}