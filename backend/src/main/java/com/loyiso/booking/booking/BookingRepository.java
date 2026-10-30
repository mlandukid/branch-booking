package com.loyiso.booking.booking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
            select b.startTime from Booking b
            where b.branch.id = :branchId and b.appointmentDate = :date
            """)
    List<LocalTime> findBookedStartTimes(@Param("branchId") Long branchId,
                                         @Param("date") LocalDate date);

    boolean existsByBranchIdAndAppointmentDateAndStartTime(Long branchId,
                                                           LocalDate appointmentDate,
                                                           LocalTime startTime);

    long countByCustomerEmailAndAppointmentDateGreaterThanEqual(String customerEmail, LocalDate from);

    @Query("select b from Booking b join fetch b.branch where b.reference = :reference")
    Optional<Booking> findByReferenceWithBranch(@Param("reference") String reference);
}