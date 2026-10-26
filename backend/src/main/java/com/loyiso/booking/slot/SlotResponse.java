package com.loyiso.booking.slot;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;

@Schema(description = "One appointment slot")
public record SlotResponse(LocalTime startTime, LocalTime endTime, boolean available) {
}