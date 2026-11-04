package com.loyiso.booking.booking;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BookingReasonFlowTests {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final LocalDate date = weekdayAhead(12);

    @Test
    void listsTheReasonsWithWhatToBring() throws Exception {
        HttpResponse<String> response = get("/api/v1/reasons");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("REPLACE_CARD").contains("Replace a card").contains("Bring your ID");
    }

    @Test
    void aBookingRecordsItsReason() throws Exception {
        HttpResponse<String> response = post("15:00", "reason-card@example.com", ",\"reason\":\"REPLACE_CARD\"");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("\"reason\":\"REPLACE_CARD\"");
    }

    @Test
    void aBookingWithoutAReasonDefaultsToOther() throws Exception {
        HttpResponse<String> response = post("15:30", "reason-none@example.com", "");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("\"reason\":\"OTHER\"");
    }

    @Test
    void anUnknownReasonIsRejected() throws Exception {
        HttpResponse<String> response = post("16:00", "reason-bad@example.com", ",\"reason\":\"WORLD_DOMINATION\"");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    private static LocalDate weekdayAhead(int minDays) {
        LocalDate day = LocalDate.now(ZoneId.of("Africa/Johannesburg")).plusDays(minDays);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }
        return day;
    }

    private HttpResponse<String> post(String time, String email, String extra) throws Exception {
        String body = """
                {"branchId":2,"date":"%s","startTime":"%s","customerName":"Reason Tester","customerEmail":"%s"%s}
                """.formatted(date, time, email, extra);
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