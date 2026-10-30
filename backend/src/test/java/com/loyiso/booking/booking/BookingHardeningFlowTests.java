package com.loyiso.booking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BookingHardeningFlowTests {

    private static final Pattern REFERENCE = Pattern.compile("\"reference\":\"([^\"]+)\"");

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void lookupMasksTheEmailAddress() throws Exception {
        LocalDate date = weekdayAhead(9);
        String body = """
                {"branchId":1,"date":"%s","startTime":"13:00","customerName":"Mask Tester","customerEmail":"masktester@example.com"}
                """.formatted(date);

        HttpResponse<String> created = post(body);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("masktester@example.com");

        Matcher matcher = REFERENCE.matcher(created.body());
        assertThat(matcher.find()).isTrue();
        HttpResponse<String> fetched = get("/api/v1/bookings/" + matcher.group(1));

        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).contains("m***@example.com").doesNotContain("masktester@example.com");
    }

    @Test
    void controlCharactersInTheNameAreRejected() throws Exception {
        String body = ("{\"branchId\":1,\"date\":\"%s\",\"startTime\":\"13:30\","
                + "\"customerName\":\"Evil\\u0007Name\",\"customerEmail\":\"evil@example.com\"}")
                .formatted(weekdayAhead(9));

        HttpResponse<String> response = post(body);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("customerName");
    }

    @Test
    void apiResponsesCarrySecurityHeaders() throws Exception {
        HttpResponse<String> response = get("/api/v1/branches");

        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
    }

    private static LocalDate weekdayAhead(int minDays) {
        LocalDate date = LocalDate.now(ZoneId.of("Africa/Johannesburg")).plusDays(minDays);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
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