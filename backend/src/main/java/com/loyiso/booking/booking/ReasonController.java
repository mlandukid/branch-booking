package com.loyiso.booking.booking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reasons")
@Tag(name = "Reasons", description = "The reasons a customer can give for visiting a branch")
public class ReasonController {

    public record ReasonResponse(String code, String label, String bring) {
    }

    @GetMapping
    @Operation(summary = "List the reasons for a visit, each with a note on what to bring")
    public List<ReasonResponse> list() {
        return Arrays.stream(BookingReason.values())
                .map(reason -> new ReasonResponse(reason.name(), reason.label(), reason.bring()))
                .toList();
    }
}