package com.loyiso.booking.booking;

import com.loyiso.booking.common.InvalidRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Bookings", description = "Book an appointment slot and look up a booking")
public class BookingController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @Operation(summary = "Book an appointment slot",
            description = "Books one slot. Each slot holds exactly one booking, enforced by the database. "
                    + "Send an Idempotency-Key header to make retries safe: repeating the same request with "
                    + "the same key returns the original booking (with Idempotent-Replayed: true) instead of "
                    + "failing, and sends no second email.")
    @ApiResponse(responseCode = "201", description = "Booking created, or the original booking replayed")
    @ApiResponse(responseCode = "400", description = "Invalid request, or the slot is not bookable")
    @ApiResponse(responseCode = "404", description = "Branch not found")
    @ApiResponse(responseCode = "409", description = "The slot has already been booked")
    @ApiResponse(responseCode = "422", description = "Booking limit reached, or the key was used for a different request")
    public ResponseEntity<BookingResponse> create(
            @Valid @RequestBody CreateBookingRequest request,
            @Parameter(description = "A unique value for each booking attempt: 8 to 64 letters, digits, hyphens or underscores.",
                    example = "7d0f1c9e-5b1c-4c0a-9f3e-2f6f4f0f9a11")
            @RequestHeader(name = IDEMPOTENCY_HEADER, required = false) String idempotencyKey) {
        CreateResult result = place(request, validKey(idempotencyKey));
        BookingResponse booking = result.booking();
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{reference}")
                .buildAndExpand(booking.reference())
                .toUri();
        ResponseEntity.BodyBuilder response = ResponseEntity.created(location);
        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(booking);
    }

    @GetMapping("/{reference}")
    @Operation(summary = "Look up a booking by its reference", description = "The customer email address is masked in this response.")
    @ApiResponse(responseCode = "200", description = "Booking found")
    @ApiResponse(responseCode = "404", description = "Booking not found")
    public BookingResponse get(@PathVariable String reference) {
        return bookingService.getByReference(reference);
    }

    private CreateResult place(CreateBookingRequest request, String key) {
        try {
            return bookingService.place(request, key);
        } catch (SlotUnavailableException ex) {
            if (key != null) {
                // The same request sent twice at once: the twin won the slot, so hand back its booking.
                Optional<BookingResponse> twin = bookingService.findByIdempotencyKey(key);
                if (twin.isPresent() && isSameRequest(twin.get(), request)) {
                    return new CreateResult(twin.get(), true);
                }
            }
            throw ex;
        }
    }

    private static boolean isSameRequest(BookingResponse booking, CreateBookingRequest request) {
        return booking.branchId().equals(request.branchId())
                && booking.date().equals(request.date())
                && booking.startTime().equals(request.startTime());
    }

    private static String validKey(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String key = header.trim();
        if (!KEY_FORMAT.matcher(key).matches()) {
            throw new InvalidRequestException(
                    "The Idempotency-Key header must be 8 to 64 letters, digits, hyphens or underscores.");
        }
        return key;
    }
}