package com.loyiso.booking.holiday;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HolidayFlowTests {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void listsTheHolidaysOf2026() throws Exception {
        HttpResponse<String> response = get("/api/v1/holidays?year=2026");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"date\":\"2026-12-25\"")
                .contains("\"code\":\"CHRISTMAS_DAY\"")
                .contains("Local Government Elections Day")
                .contains("National Women's Day (observed)");
    }

    @Test
    void anAbsurdYearIsRejected() throws Exception {
        assertThat(get("/api/v1/holidays?year=1800").statusCode()).isEqualTo(400);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}