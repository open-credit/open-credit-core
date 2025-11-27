package com.lumexpay.vortexa.credit.scheduler;

import com.lumexpay.vortexa.credit.repository.CreditAssessmentRepository;
import com.lumexpay.vortexa.credit.service.CreditAssessmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduler for periodic credit re-assessment of active merchants.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CreditReassessmentScheduler {

    private final CreditAssessmentService creditAssessmentService;
    private final CreditAssessmentRepository creditAssessmentRepository;

    @Value("${scheduler.reassessment.enabled:true}")
    private boolean schedulerEnabled;

    /**
     * Re-assess all merchants with assessments older than 30 days.
     * Runs at 2 AM on the 1st of every month.
     */
    @Scheduled(cron = "${scheduler.reassessment.cron:0 0 2 1 * ?}")
    public void reAssessActiveMerchants() {
        if (!schedulerEnabled) {
            log.info("Credit re-assessment scheduler is disabled");
            return;
        }

        log.info("Starting scheduled credit re-assessment job");
        long startTime = System.currentTimeMillis();

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<String> merchantsToReassess = creditAssessmentRepository
            .findMerchantsNeedingReassessment(thirtyDaysAgo);

        log.info("Found {} merchants needing re-assessment", merchantsToReassess.size());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (String merchantId : merchantsToReassess) {
            try {
                log.debug("Re-assessing merchant: {}", merchantId);
                creditAssessmentService.reassess(merchantId);
                successCount.incrementAndGet();
            } catch (Exception e) {
                log.error("Failed to re-assess merchant: {}", merchantId, e);
                failureCount.incrementAndGet();
            }

            // Add small delay between assessments to avoid overwhelming the UPI platform
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Re-assessment job interrupted");
                break;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Credit re-assessment job completed in {}ms. Success: {}, Failures: {}",
            duration, successCount.get(), failureCount.get());
    }

    /**
     * Clean up old assessment records (older than 2 years).
     * Runs at 3 AM on the 1st of January every year.
     */
    @Scheduled(cron = "0 0 3 1 1 ?")
    public void cleanupOldAssessments() {
        if (!schedulerEnabled) {
            log.info("Cleanup scheduler is disabled");
            return;
        }

        log.info("Starting cleanup of old assessment records");
        LocalDateTime twoYearsAgo = LocalDateTime.now().minusYears(2);

        try {
            creditAssessmentRepository.deleteByAssessmentDateBefore(twoYearsAgo);
            log.info("Successfully cleaned up assessment records older than {}", twoYearsAgo);
        } catch (Exception e) {
            log.error("Failed to cleanup old assessment records", e);
        }
    }

    /**
     * Log daily statistics at midnight.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void logDailyStatistics() {
        if (!schedulerEnabled) {
            return;
        }

        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);

        try {
            Double avgScore = creditAssessmentRepository.getAverageCreditScore(oneDayAgo);
            log.info("Daily Statistics - Average Credit Score (last 24h): {}", 
                avgScore != null ? String.format("%.1f", avgScore) : "N/A");

            creditAssessmentRepository.getRiskCategoryStatistics(oneDayAgo)
                .forEach(stat -> log.info("Risk Category: {} - Count: {}", stat[0], stat[1]));
        } catch (Exception e) {
            log.warn("Failed to generate daily statistics", e);
        }
    }
}
