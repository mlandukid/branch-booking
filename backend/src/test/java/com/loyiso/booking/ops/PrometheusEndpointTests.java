package com.loyiso.booking.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the business counters reach the scrape endpoint. Spring Boot tests switch metrics export off
 * by default, so this test switches the Prometheus export back on. If anything is missing, the
 * failure message says what the endpoint returned.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.prometheus.metrics.export.enabled=true")
@ActiveProfiles("test")
class PrometheusEndpointTests {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void bookingCountersCanBeScraped() throws Exception {
        String body = """
                {"branchId":3,"date":"%s","startTime":"08:20","customerName":"Scrape Tester","customerEmail":"scrape@example.com"}
                """.formatted(weekdayAhead(18));
        HttpRequest booking = HttpRequest.newBuilder(URI.create(url("/api/v1/bookings")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        assertThat(http.send(booking, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(201);

        HttpResponse<String> metrics = get("/actuator/prometheus");
        String start = metrics.body().substring(0, Math.min(400, metrics.body().length()));
        String index = get("/actuator").body();

        assertThat(metrics.statusCode())
                .as("status of GET /actuator/prometheus, which began with: %s. The actuator index is: %s", start, index)
                .isEqualTo(200);
        assertThat(metrics.body())
                .as("the booking counter in the scrape output, which began with: %s", start)
                .contains("bookings_confirmed_total");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url(path))).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static LocalDate weekdayAhead(int minDays) {
        LocalDate day = LocalDate.now(ZoneId.of("Africa/Johannesburg")).plusDays(minDays);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }
        return day;
    }
}