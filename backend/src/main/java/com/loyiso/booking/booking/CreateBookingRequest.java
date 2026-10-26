package com.loyiso.booking.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Request to book one appointment slot")
public record CreateBookingRequest(
        @Schema(example = "1")
        @NotNull(message = "must not be null")
        Long branchId,

        @Schema(example = "2026-11-02", description = "Appointment date, ISO format")
        @NotNull(message = "must not be null")
        LocalDate date,

        @Schema(example = "09:00", description = "Slot start time, as returned by the slots endpoint")
        @NotNull(message = "must not be null")
        LocalTime startTime,

        @Schema(example = "Thandi Nkosi")
        @NotBlank(message = "must not be blank")
        @Size(max = 120, message = "must be at most 120 characters")
        String customerName,

        @Schema(example = "thandi@example.com")
        @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid email address")
        @Size(max = 254, message = "must be at most 254 characters")
        String customerEmail) {
}