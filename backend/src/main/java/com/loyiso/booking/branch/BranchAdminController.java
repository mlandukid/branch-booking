package com.loyiso.booking.branch;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/admin/branches")
@Tag(name = "Branch administration",
        description = "Create and update branches. Switched off (404) unless ADMIN_API_KEY is set.")
public class BranchAdminController {

    private static final String KEY_DESCRIPTION = "The admin key. It is checked before anything else happens.";

    private final BranchAdminService service;

    public BranchAdminController(BranchAdminService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a branch")
    @ApiResponse(responseCode = "201", description = "Branch created")
    @ApiResponse(responseCode = "400", description = "Invalid details")
    @ApiResponse(responseCode = "401", description = "Missing or wrong admin key")
    @ApiResponse(responseCode = "404", description = "The admin API is switched off")
    @ApiResponse(responseCode = "409", description = "A branch with that name already exists")
    public ResponseEntity<BranchResponse> create(
            @Valid @RequestBody BranchRequest request,
            @Parameter(description = KEY_DESCRIPTION) @RequestHeader(name = "X-Admin-Key", required = false) String adminKey) {
        Branch branch = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/branches/{id}")
                .buildAndExpand(branch.getId())
                .toUri();
        return ResponseEntity.created(location).body(BranchResponse.from(branch));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a branch",
            description = "Opening hours and appointment length cannot change while the branch has upcoming bookings.")
    @ApiResponse(responseCode = "200", description = "Branch updated")
    @ApiResponse(responseCode = "400", description = "Invalid details")
    @ApiResponse(responseCode = "401", description = "Missing or wrong admin key")
    @ApiResponse(responseCode = "404", description = "Unknown branch, or the admin API is switched off")
    @ApiResponse(responseCode = "409", description = "Name taken, or the schedule cannot change yet")
    public BranchResponse update(
            @PathVariable Long id,
            @Valid @RequestBody BranchRequest request,
            @Parameter(description = KEY_DESCRIPTION) @RequestHeader(name = "X-Admin-Key", required = false) String adminKey) {
        return BranchResponse.from(service.update(id, request));
    }
}