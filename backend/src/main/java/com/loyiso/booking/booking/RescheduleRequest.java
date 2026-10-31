package com.loyiso.booking.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "The new slot for an existing booking. The branch stays the same.")
public record RescheduleRequest(
        @Schema(example = "2026-11-09")
        @NotNull(message = "must not be null")
        LocalDate date,

        @Schema(example = "10:30")
        @NotNull(message = "must not be null")
        LocalTime startTime) {
}