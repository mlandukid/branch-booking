package com.loyiso.booking.branch;

import com.loyiso.booking.booking.BookingRepository;
import com.loyiso.booking.common.ConflictException;
import com.loyiso.booking.common.InvalidRequestException;
import com.loyiso.booking.common.ResourceNotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and updates branches. The opening hours and slot length define the slot grid, so they
 * cannot change while the branch has upcoming bookings: moving the grid would strand them.
 */
@Service
public class BranchAdminService {

    private final BranchRepository branchRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;

    public BranchAdminService(BranchRepository branchRepository, BookingRepository bookingRepository, Clock clock) {
        this.branchRepository = branchRepository;
        this.bookingRepository = bookingRepository;
        this.clock = clock;
    }

    @Transactional
    public Branch create(BranchRequest request) {
        validateHours(request);
        String name = request.name().trim();
        if (branchRepository.existsByNameIgnoreCase(name)) {
            throw nameTaken();
        }
        try {
            return branchRepository.saveAndFlush(new Branch(name, request.address().trim(), request.city().trim(),
                    request.opensAt(), request.closesAt(), request.slotMinutes()));
        } catch (DataIntegrityViolationException ex) {
            throw nameTaken();
        }
    }

    @Transactional
    public Branch update(Long id, BranchRequest request) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch %d not found".formatted(id)));
        validateHours(request);
        String name = request.name().trim();
        if (branchRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw nameTaken();
        }

        boolean scheduleChanged = !branch.getOpensAt().equals(request.opensAt())
                || !branch.getClosesAt().equals(request.closesAt())
                || branch.getSlotMinutes() != request.slotMinutes();
        if (scheduleChanged
                && bookingRepository.existsByBranchIdAndAppointmentDateGreaterThanEqual(id, LocalDate.now(clock))) {
            throw new ConflictException("This branch has upcoming bookings, so its opening hours and "
                    + "appointment length cannot change until they have passed.");
        }

        branch.update(name, request.address().trim(), request.city().trim(),
                request.opensAt(), request.closesAt(), request.slotMinutes());
        try {
            return branchRepository.saveAndFlush(branch);
        } catch (DataIntegrityViolationException ex) {
            throw nameTaken();
        }
    }

    private static void validateHours(BranchRequest request) {
        if (!request.opensAt().isBefore(request.closesAt())) {
            throw new InvalidRequestException("The branch must open before it closes.");
        }
        long openMinutes = Duration.between(request.opensAt(), request.closesAt()).toMinutes();
        if (openMinutes < request.slotMinutes()) {
            throw new InvalidRequestException("The opening hours must fit at least one appointment.");
        }
    }

    private static ConflictException nameTaken() {
        return new ConflictException("A branch with that name already exists.");
    }
}