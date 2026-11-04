package com.loyiso.booking.outbox;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OutboxEmailRepository extends JpaRepository<OutboxEmail, Long> {

    @Query("""
            select e from OutboxEmail e
            where e.status = 'PENDING' and e.nextAttemptAt <= :now
              and (e.claimedUntil is null or e.claimedUntil < :now)
            order by e.id
            """)
    List<OutboxEmail> findDue(@Param("now") Instant now, Pageable pageable);

    /** Takes a row for this instance. Returns 0 if another instance got there first. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OutboxEmail e set e.claimedUntil = :until, e.attempts = e.attempts + 1
            where e.id = :id and e.status = 'PENDING'
              and (e.claimedUntil is null or e.claimedUntil < :now)
            """)
    int claim(@Param("id") Long id, @Param("now") Instant now, @Param("until") Instant until);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update OutboxEmail e set e.status = 'SENT', e.sentAt = :now, e.body = '',
                e.claimedUntil = null, e.lastError = null
            where e.id = :id
            """)
    int markSent(@Param("id") Long id, @Param("now") Instant now);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update OutboxEmail e set e.status = :status, e.nextAttemptAt = :next,
                e.claimedUntil = null, e.lastError = :error
            where e.id = :id
            """)
    int markFailed(@Param("id") Long id, @Param("status") String status,
                   @Param("next") Instant next, @Param("error") String error);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("delete from OutboxEmail e where e.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    long countByStatus(String status);
}