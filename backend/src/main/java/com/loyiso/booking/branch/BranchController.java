package com.loyiso.booking.branch;

import com.loyiso.booking.slot.SlotResponse;
import com.loyiso.booking.slot.SlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/branches")
@Tag(name = "Branches", description = "Browse branches and their appointment slots")
public class BranchController {

    private final BranchService branchService;
    private final SlotService slotService;

    public BranchController(BranchService branchService, SlotService slotService) {
        this.branchService = branchService;
        this.slotService = slotService;
    }

    @GetMapping
    @Operation(summary = "List all branches")
    public List<BranchResponse> list() {
        return branchService.findAll().stream().map(BranchResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one branch")
    public BranchResponse get(@PathVariable Long id) {
        return BranchResponse.from(branchService.getById(id));
    }

    @GetMapping("/{id}/slots")
    @Operation(summary = "List the appointment slots for a branch on a date",
            description = "Returns every slot in the day. Slots that are already booked, or that "
                    + "start in the past, are returned with available=false.")
    public List<SlotResponse> slots(
            @PathVariable Long id,
            @Parameter(description = "Date in ISO format, for example 2026-11-02")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return slotService.findSlots(id, date);
    }
}