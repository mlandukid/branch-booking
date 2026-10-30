package com.loyiso.booking.booking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loyiso.booking.common.GlobalExceptionHandler;
import com.loyiso.booking.common.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BookingControllerTest {

    private static final String VALID_BODY = """
            {"branchId":1,"date":"2026-11-02","startTime":"09:00",
             "customerName":"Thandi Nkosi","customerEmail":"thandi@example.com"}
            """;

    private BookingService bookingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        bookingService = mock(BookingService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BookingController(bookingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private BookingResponse sampleResponse() {
        return new BookingResponse("abc-123", 1L, "Sandton", LocalDate.of(2026, 11, 2),
                LocalTime.of(9, 0), LocalTime.of(9, 30), "Thandi Nkosi", "thandi@example.com",
                Instant.parse("2026-10-26T08:00:00Z"));
    }

    @Test
    void createsBookingAndReturnsLocationHeader() throws Exception {
        when(bookingService.place(any(CreateBookingRequest.class), any()))
                .thenReturn(new CreateResult(sampleResponse(), false));

        mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/bookings/abc-123"))
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.reference").value("abc-123"))
                .andExpect(jsonPath("$.branchName").value("Sandton"));
    }

    @Test
    void aReplayedBookingIsFlagged() throws Exception {
        when(bookingService.place(any(CreateBookingRequest.class), any()))
                .thenReturn(new CreateResult(sampleResponse(), true));

        mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "retry-key-12345").content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.reference").value("abc-123"));
    }

    @Test
    void invalidBodyReturns400() throws Exception {
        String invalid = """
                {"branchId":1,"date":"2026-11-02","startTime":"09:00",
                 "customerName":"","customerEmail":"not-an-email"}
                """;

        mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void malformedIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "x").content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "The Idempotency-Key header must be 8 to 64 letters, digits, hyphens or underscores."));
    }

    @Test
    void takenSlotReturns409() throws Exception {
        when(bookingService.place(any(CreateBookingRequest.class), any()))
                .thenThrow(new SlotUnavailableException("That slot is no longer available."));

        mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("That slot is no longer available."));
    }

    @Test
    void theLoserOfASameKeyRaceGetsTheWinnersBooking() throws Exception {
        when(bookingService.place(any(CreateBookingRequest.class), any()))
                .thenThrow(new SlotUnavailableException("That slot is no longer available."));
        when(bookingService.findByIdempotencyKey("twin-key-12345")).thenReturn(Optional.of(sampleResponse()));

        mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "twin-key-12345").content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.reference").value("abc-123"));
    }

    @Test
    void aKeyBelongingToADifferentSlotDoesNotHideTheConflict() throws Exception {
        BookingResponse other = new BookingResponse("zzz-999", 1L, "Sandton", LocalDate.of(2026, 11, 2),
                LocalTime.of(10, 0), LocalTime.of(10, 30), "Someone", "someone@example.com",
                Instant.parse("2026-10-26T08:00:00Z"));
        when(bookingService.place(any(CreateBookingRequest.class), any()))
                .thenThrow(new SlotUnavailableException("That slot is no longer available."));
        when(bookingService.findByIdempotencyKey("twin-key-12345")).thenReturn(Optional.of(other));

        mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "twin-key-12345").content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void returnsExistingBooking() throws Exception {
        when(bookingService.getByReference("abc-123")).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/bookings/abc-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerEmail").value("thandi@example.com"));
    }

    @Test
    void unknownReferenceReturns404() throws Exception {
        when(bookingService.getByReference("nope"))
                .thenThrow(new ResourceNotFoundException("Booking nope not found"));

        mockMvc.perform(get("/api/v1/bookings/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}