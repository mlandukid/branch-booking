package com.loyiso.booking.branch;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;

@Schema(description = "A bank branch that accepts appointments")
public record BranchResponse(
        Long id,
        String name,
        String address,
        String city,
        LocalTime opensAt,
        LocalTime closesAt,
        int slotMinutes) {

    public static BranchResponse from(Branch branch) {
        return new BranchResponse(
                branch.getId(),
                branch.getName(),
                branch.getAddress(),
                branch.getCity(),
                branch.getOpensAt(),
                branch.getClosesAt(),
                branch.getSlotMinutes());
    }
}