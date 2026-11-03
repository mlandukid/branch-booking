package com.loyiso.booking.branch;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

@Schema(description = "The details of a branch")
public record BranchRequest(
        @Schema(example = "Pretoria Central")
        @NotBlank(message = "must not be blank")
        @Size(max = 100, message = "must be at most 100 characters")
        String name,

        @Schema(example = "Church Square")
        @NotBlank(message = "must not be blank")
        @Size(max = 255, message = "must be at most 255 characters")
        String address,

        @Schema(example = "Pretoria")
        @NotBlank(message = "must not be blank")
        @Size(max = 100, message = "must be at most 100 characters")
        String city,

        @Schema(example = "08:00")
        @NotNull(message = "must not be null")
        LocalTime opensAt,

        @Schema(example = "16:00")
        @NotNull(message = "must not be null")
        LocalTime closesAt,

        @Schema(example = "30", description = "Length of one appointment in minutes")
        @NotNull(message = "must not be null")
        @Min(value = 5, message = "must be at least 5")
        @Max(value = 240, message = "must be at most 240")
        Integer slotMinutes) {
}