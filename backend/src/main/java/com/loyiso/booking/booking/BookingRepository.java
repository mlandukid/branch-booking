package com.loyiso.booking.booking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
}