package com.loyiso.booking.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the clean-up every night. With several instances every one of them runs it, which is safe
 * because deleting the same old rows twice does nothing. A lock such as ShedLock would stop the
 * duplicate work.
 */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final RetentionService retentionService;

    public RetentionJob(RetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(cron = "${booking.retention-cron:0 30 3 * * *}", zone = "${booking.timezone:Africa/Johannesburg}")
    public void run() {
        try {
            retentionService.purgeExpired();
        } catch (Exception ex) {
            log.error("The data retention job failed", ex);
        }
    }
}