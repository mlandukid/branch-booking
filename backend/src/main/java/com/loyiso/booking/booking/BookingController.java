package com.loyiso.booking.booking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Bookings", description = "Book an appointment slot and look up a booking")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @Operation(summary = "Book an appointment slot",
            description = "Books one slot. Each slot holds exactly one booking, enforced by the database.")
    @ApiResponse(responseCode = "201", description = "Booking created")
    @ApiResponse(responseCode = "400", description = "Invalid request, or the slot is not bookable")
    @ApiResponse(responseCode = "404", description = "Branch not found")
    @ApiResponse(responseCode = "409", description = "The slot has already been booked")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request) {
        BookingResponse booking = bookingService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{reference}")
                .buildAndExpand(booking.reference())
                .toUri();
        return ResponseEntity.created(location).body(booking);
    }

    @GetMapping("/{reference}")
    @Operation(summary = "Look up a booking by its reference")
    @ApiResponse(responseCode = "200", description = "Booking found")
    @ApiResponse(responseCode = "404", description = "Booking not found")
    public BookingResponse get(@PathVariable String reference) {
        return bookingService.getByReference(reference);
    }
}