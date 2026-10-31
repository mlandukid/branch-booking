package com.loyiso.booking.booking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings/{reference}")
@Tag(name = "Manage booking", description = "Change or cancel a booking with the private token from the confirmation email")
public class BookingManagementController {

    public static final String TOKEN_HEADER = "X-Manage-Token";

    private static final String TOKEN_DESCRIPTION =
            "The private token returned when the booking was created and sent in the confirmation email.";

    private final BookingChangeService changeService;

    public BookingManagementController(BookingChangeService changeService) {
        this.changeService = changeService;
    }

    @GetMapping("/manage")
    @Operation(summary = "View a booking you are allowed to manage",
            description = "A wrong token and an unknown reference both answer 404, so bookings cannot be probed.")
    @ApiResponse(responseCode = "200", description = "The booking, with the email address masked")
    @ApiResponse(responseCode = "404", description = "Unknown reference or wrong token")
    public BookingResponse view(
            @PathVariable String reference,
            @Parameter(description = TOKEN_DESCRIPTION) @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return changeService.view(reference, token);
    }

    @PatchMapping
    @Operation(summary = "Move a booking to another slot at the same branch",
            description = "The old slot is freed. Changes are not allowed shortly before the appointment starts.")
    @ApiResponse(responseCode = "200", description = "The moved booking")
    @ApiResponse(responseCode = "400", description = "The new time is not a valid, bookable slot")
    @ApiResponse(responseCode = "404", description = "Unknown reference or wrong token")
    @ApiResponse(responseCode = "409", description = "The new slot is taken, the booking changed at the same moment, or it is too late to change it")
    public BookingResponse reschedule(
            @PathVariable String reference,
            @Parameter(description = TOKEN_DESCRIPTION) @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody RescheduleRequest request) {
        return changeService.reschedule(reference, token, request.date(), request.startTime());
    }

    @DeleteMapping
    @Operation(summary = "Cancel a booking",
            description = "The slot becomes available to other customers straight away.")
    @ApiResponse(responseCode = "204", description = "Cancelled")
    @ApiResponse(responseCode = "404", description = "Unknown reference or wrong token")
    @ApiResponse(responseCode = "409", description = "The booking changed at the same moment, or it is too late to cancel it")
    public ResponseEntity<Void> cancel(
            @PathVariable String reference,
            @Parameter(description = TOKEN_DESCRIPTION) @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        changeService.cancel(reference, token);
        return ResponseEntity.noContent().build();
    }
}