package com.loyiso.booking.booking;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    @Query("select k from IdempotencyKey k join fetch k.booking b join fetch b.branch where k.keyValue = :key")
    Optional<IdempotencyKey> findWithBooking(@Param("key") String key);
}