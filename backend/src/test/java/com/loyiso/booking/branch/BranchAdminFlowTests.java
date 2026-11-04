package com.loyiso.booking.branch;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "booking.admin.api-key=test-admin-key")
@ActiveProfiles("test")
class BranchAdminFlowTests {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void withoutTheKeyTheAdminApiIsRefused() throws Exception {
        assertThat(send("POST", "/api/v1/admin/branches", json(unique(), "08:00", "16:00", 30), null).statusCode()).isEqualTo(401);
    }

    @Test
    void aWrongKeyIsRefused() throws Exception {
        assertThat(send("POST", "/api/v1/admin/branches", json(unique(), "08:00", "16:00", 30), "not-the-key").statusCode()).isEqualTo(401);
    }

    @Test
    void createsABranchThatThenAppearsInTheList() throws Exception {
        String name = unique();

        HttpResponse<String> created = send("POST", "/api/v1/admin/branches", json(name, "08:00", "16:00", 30), "test-admin-key");

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("Location")).isPresent();
        assertThat(send("GET", "/api/v1/branches", null, null).body()).contains(name);
    }

    @Test
    void updatesABranchThatHasNoBookings() throws Exception {
        String name = unique();
        HttpResponse<String> created = send("POST", "/api/v1/admin/branches", json(name, "08:00", "16:00", 30), "test-admin-key");
        String id = created.body().replaceAll(".*\"id\":(\\d+).*", "$1");

        HttpResponse<String> updated = send("PUT", "/api/v1/admin/branches/" + id, json(name, "09:00", "15:00", 20), "test-admin-key");

        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(updated.body()).contains("\"slotMinutes\":20");
    }

    @Test
    void aDuplicateNameIsRejected() throws Exception {
        String name = unique();
        send("POST", "/api/v1/admin/branches", json(name, "08:00", "16:00", 30), "test-admin-key");

        HttpResponse<String> second = send("POST", "/api/v1/admin/branches", json(name, "08:00", "16:00", 30), "test-admin-key");

        assertThat(second.statusCode()).isEqualTo(409);
    }

    @Test
    void hoursThatCannotFitAnAppointmentAreRejected() throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/admin/branches", json(unique(), "08:00", "08:20", 30), "test-admin-key");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    private static String unique() {
        return "Test Branch " + UUID.randomUUID();
    }

    private static String json(String name, String opens, String closes, int slot) {
        return """
                {"name":"%s","address":"1 Test Street","city":"Testville","opensAt":"%s","closesAt":"%s","slotMinutes":%d}
                """.formatted(name, opens, closes, slot);
    }

    private HttpResponse<String> send(String method, String path, String body, String key) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (key != null) {
            builder.header("X-Admin-Key", key);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}