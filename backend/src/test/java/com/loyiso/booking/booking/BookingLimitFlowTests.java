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
class BookingLimitFlowTests {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void fourthUpcomingBookingForTheSameEmailIsRejected() throws Exception {
        LocalDate date = weekdayAhead(7);
        String email = "limit@example.com";

        for (String time : new String[] {"11:00", "11:30", "12:00"}) {
            assertThat(post(json(date, time, email)).statusCode()).isEqualTo(201);
        }

        HttpResponse<String> fourth = post(json(date, "12:30", email));

        assertThat(fourth.statusCode()).isEqualTo(422);
        assertThat(fourth.body()).contains("maximum of 3");
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
                {"branchId":1,"date":"%s","startTime":"%s","customerName":"Limit Tester","customerEmail":"%s"}
                """.formatted(date, time, email);
    }

    private HttpResponse<String> post(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/bookings"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}