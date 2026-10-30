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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The slot guarantee must stop double bookings without ever blocking bookings for other slots. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BookingConcurrencyTests {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private BookingRepository bookingRepository;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void differentSlotsBookedAtTheSameMomentAllSucceed() throws Exception {
        LocalDate date = weekdayAhead(8);
        String[] times = {"10:00", "10:30", "11:00", "11:30", "12:00", "12:30"};

        ExecutorService pool = Executors.newFixedThreadPool(times.length);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < times.length; i++) {
            int n = i;
            futures.add(pool.submit(() -> {
                go.await();
                return post(json(date, times[n], "Parallel " + n, "parallel" + n + "@example.com")).statusCode();
            }));
        }
        go.countDown();

        for (Future<Integer> future : futures) {
            assertThat(future.get(60, TimeUnit.SECONDS)).isEqualTo(201);
        }
        pool.shutdown();

        assertThat(bookingRepository.findBookedStartTimes(2L, date)).hasSize(times.length);
    }

    private static LocalDate weekdayAhead(int minDays) {
        LocalDate date = LocalDate.now(ZoneId.of("Africa/Johannesburg")).plusDays(minDays);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }

    private static String json(LocalDate date, String time, String name, String email) {
        return """
                {"branchId":2,"date":"%s","startTime":"%s","customerName":"%s","customerEmail":"%s"}
                """.formatted(date, time, name, email);
    }

    private HttpResponse<String> post(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/bookings"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}