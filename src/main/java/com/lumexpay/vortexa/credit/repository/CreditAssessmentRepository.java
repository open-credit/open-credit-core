package com.lumexpay.vortexa.credit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.lumexpay.vortexa.credit.model.CreditAssessment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for credit assessment data access.
 */
@Repository
public interface CreditAssessmentRepository extends JpaRepository<CreditAssessment, UUID> {

    /**
     * Find the latest assessment for a merchant.
     */
    Optional<CreditAssessment> findTopByMerchantIdOrderByAssessmentDateDesc(String merchantId);

    /**
     * Find all assessments for a merchant.
     */
    List<CreditAssessment> findByMerchantIdOrderByAssessmentDateDesc(String merchantId);

    /**
     * Find assessments within a date range.
     */
    List<CreditAssessment> findByAssessmentDateBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Find assessments by risk category.
     */
    List<CreditAssessment> findByRiskCategory(CreditAssessment.RiskCategory riskCategory);

    /**
     * Find eligible merchants.
     */
    List<CreditAssessment> findByIsEligibleTrue();

    /**
     * Find merchants with credit score above threshold.
     */
    List<CreditAssessment> findByCreditScoreGreaterThanEqual(Integer score);

    /**
     * Check if merchant has recent assessment.
     */
    @Query("SELECT COUNT(c) > 0 FROM CreditAssessment c " +
           "WHERE c.merchantId = :merchantId " +
           "AND c.assessmentDate > :since")
    boolean hasRecentAssessment(@Param("merchantId") String merchantId, 
                                @Param("since") LocalDateTime since);

    /**
     * Get latest assessment for each merchant (for batch operations).
     */
    @Query(value = "SELECT DISTINCT ON (merchant_id) * FROM credit_assessments " +
                   "ORDER BY merchant_id, assessment_date DESC",
           nativeQuery = true)
    List<CreditAssessment> findLatestAssessmentsForAllMerchants();

    /**
     * Find merchants needing re-assessment (no assessment in last N days).
     */
    @Query("SELECT DISTINCT c.merchantId FROM CreditAssessment c " +
           "WHERE c.merchantId NOT IN (" +
           "  SELECT ca.merchantId FROM CreditAssessment ca " +
           "  WHERE ca.assessmentDate > :since" +
           ")")
    List<String> findMerchantsNeedingReassessment(@Param("since") LocalDateTime since);

    /**
     * Get assessment statistics.
     */
    @Query("SELECT c.riskCategory, COUNT(c) FROM CreditAssessment c " +
           "WHERE c.assessmentDate > :since " +
           "GROUP BY c.riskCategory")
    List<Object[]> getRiskCategoryStatistics(@Param("since") LocalDateTime since);

    /**
     * Get average credit score by risk category.
     */
    @Query("SELECT AVG(c.creditScore) FROM CreditAssessment c " +
           "WHERE c.assessmentDate > :since")
    Double getAverageCreditScore(@Param("since") LocalDateTime since);

    /**
     * Delete old assessments (for data retention).
     */
    void deleteByAssessmentDateBefore(LocalDateTime cutoff);
}
