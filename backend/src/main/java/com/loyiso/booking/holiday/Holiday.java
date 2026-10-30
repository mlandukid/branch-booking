package com.loyiso.booking.holiday;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "A South African public holiday. Branches are closed on these days.")
public record Holiday(
        @Schema(example = "2026-12-25") LocalDate date,
        @Schema(example = "CHRISTMAS_DAY", description = "Stable identifier, safe to use as a lookup key") String code,
        @Schema(example = "Christmas Day") String name) {
}