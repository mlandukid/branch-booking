package com.loyiso.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.loyiso.booking.branch.BranchRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the whole application against an in-memory database. Flyway runs the real
 * migrations and Hibernate validates the entities against them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BookingApplicationTests {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private BranchRepository branchRepository;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void migrationsRunAndSeedDataIsPresent() {
        assertThat(branchRepository.count()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void listsBranchesOverHttp() throws Exception {
        HttpResponse<String> response = get("/api/v1/branches");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Sandton");
    }

    @Test
    void returnsSlotsForTheNextWeekday() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }

        HttpResponse<String> response = get("/api/v1/branches/1/slots?date=" + date);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"available\":true");
    }

    @Test
    void unknownBranchReturns404() throws Exception {
        HttpResponse<String> response = get("/api/v1/branches/9999");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("Branch 9999 not found");
    }

    @Test
    void malformedDateReturns400() throws Exception {
        HttpResponse<String> response = get("/api/v1/branches/1/slots?date=not-a-date");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void servesOpenApiDocumentation() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("/api/v1/branches");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}