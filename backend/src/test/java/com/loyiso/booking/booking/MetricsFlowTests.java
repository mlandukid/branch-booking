package com.loyiso.booking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MetricsFlowTests {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private MeterRegistry registry;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aBookingIsCountedOnceItHasCommitted() throws Exception {
        double before = registry.counter("bookings.created", "branch", "Sandton", "reason", "OTHER").count();
        LocalDate date = weekdayAhead(16);
        String body = """
                {"branchId":1,"date":"%s","startTime":"09:00","customerName":"Metrics Tester","customerEmail":"metrics@example.com"}
                """.formatted(date);

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/bookings"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(registry.counter("bookings.created", "branch", "Sandton", "reason", "OTHER").count())
                .isEqualTo(before + 1);
    }

    private static LocalDate weekdayAhead(int minDays) {
        LocalDate day = LocalDate.now(ZoneId.of("Africa/Johannesburg")).plusDays(minDays);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }
        return day;
    }
}