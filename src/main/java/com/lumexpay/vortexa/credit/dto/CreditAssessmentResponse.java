package com.lumexpay.vortexa.credit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lumexpay.vortexa.credit.model.CreditAssessment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * API response DTO for credit assessment results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditAssessmentResponse {

    @JsonProperty("assessment_id")
    private UUID assessmentId;

    @JsonProperty("merchant_id")
    private String merchantId;

    @JsonProperty("assessment_date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime assessmentDate;

    // ==========================================
    // CREDIT SCORE SUMMARY
    // ==========================================

    @JsonProperty("credit_score")
    private Integer creditScore;

    @JsonProperty("risk_category")
    private String riskCategory;

    @JsonProperty("risk_description")
    private String riskDescription;

    // ==========================================
    // ELIGIBILITY
    // ==========================================

    @JsonProperty("is_eligible")
    private Boolean isEligible;

    @JsonProperty("eligible_loan_amount")
    private BigDecimal eligibleLoanAmount;

    @JsonProperty("max_tenure_days")
    private Integer maxTenureDays;

    @JsonProperty("recommended_interest_rate")
    private BigDecimal recommendedInterestRate;

    @JsonProperty("ineligibility_reason")
    private String ineligibilityReason;

    // ==========================================
    // FINANCIAL METRICS
    // ==========================================

    @JsonProperty("financial_metrics")
    private FinancialMetricsSummary financialMetrics;

    // ==========================================
    // PERFORMANCE METRICS
    // ==========================================

    @JsonProperty("performance_metrics")
    private PerformanceMetricsSummary performanceMetrics;

    // ==========================================
    // COMPONENT SCORES
    // ==========================================

    @JsonProperty("score_breakdown")
    private ScoreBreakdown scoreBreakdown;

    // ==========================================
    // WARNINGS & STRENGTHS
    // ==========================================

    @JsonProperty("warnings")
    private List<String> warnings;

    @JsonProperty("strengths")
    private List<String> strengths;

    // ==========================================
    // REPORT
    // ==========================================

    @JsonProperty("report_url")
    private String reportUrl;

    /**
     * Financial metrics summary
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialMetricsSummary {
        @JsonProperty("last_3_months_volume")
        private BigDecimal last3MonthsVolume;

        @JsonProperty("last_6_months_volume")
        private BigDecimal last6MonthsVolume;

        @JsonProperty("last_12_months_volume")
        private BigDecimal last12MonthsVolume;

        @JsonProperty("average_monthly_volume")
        private BigDecimal averageMonthlyVolume;

        @JsonProperty("average_transaction_value")
        private BigDecimal averageTransactionValue;

        @JsonProperty("transaction_count")
        private Integer transactionCount;

        @JsonProperty("unique_customer_count")
        private Integer uniqueCustomerCount;
    }

    /**
     * Performance metrics summary
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetricsSummary {
        @JsonProperty("consistency_score")
        private BigDecimal consistencyScore;

        @JsonProperty("growth_rate")
        private BigDecimal growthRate;

        @JsonProperty("bounce_rate")
        private BigDecimal bounceRate;

        @JsonProperty("customer_concentration")
        private BigDecimal customerConcentration;
    }

    /**
     * Score breakdown
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreBreakdown {
        @JsonProperty("volume_score")
        private BigDecimal volumeScore;

        @JsonProperty("volume_weight")
        private BigDecimal volumeWeight;

        @JsonProperty("consistency_score")
        private BigDecimal consistencyScore;

        @JsonProperty("consistency_weight")
        private BigDecimal consistencyWeight;

        @JsonProperty("growth_score")
        private BigDecimal growthScore;

        @JsonProperty("growth_weight")
        private BigDecimal growthWeight;

        @JsonProperty("bounce_rate_score")
        private BigDecimal bounceRateScore;

        @JsonProperty("bounce_rate_weight")
        private BigDecimal bounceRateWeight;

        @JsonProperty("concentration_score")
        private BigDecimal concentrationScore;

        @JsonProperty("concentration_weight")
        private BigDecimal concentrationWeight;
    }

    /**
     * Create response from entity
     */
    public static CreditAssessmentResponse fromEntity(CreditAssessment entity) {
        return CreditAssessmentResponse.builder()
            .assessmentId(entity.getAssessmentId())
            .merchantId(entity.getMerchantId())
            .assessmentDate(entity.getAssessmentDate())
            .creditScore(entity.getCreditScore())
            .riskCategory(entity.getRiskCategory().name())
            .riskDescription(entity.getRiskCategory().getDescription())
            .isEligible(entity.getIsEligible())
            .eligibleLoanAmount(entity.getEligibleLoanAmount())
            .maxTenureDays(entity.getMaxTenureDays())
            .recommendedInterestRate(entity.getRecommendedInterestRate())
            .ineligibilityReason(entity.getIneligibilityReason())
            .financialMetrics(FinancialMetricsSummary.builder()
                .last3MonthsVolume(entity.getLast3MonthsVolume())
                .last6MonthsVolume(entity.getLast6MonthsVolume())
                .last12MonthsVolume(entity.getLast12MonthsVolume())
                .averageMonthlyVolume(entity.getAverageMonthlyVolume())
                .averageTransactionValue(entity.getAverageTransactionValue())
                .transactionCount(entity.getTransactionCount())
                .uniqueCustomerCount(entity.getUniqueCustomerCount())
                .build())
            .performanceMetrics(PerformanceMetricsSummary.builder()
                .consistencyScore(entity.getConsistencyScore())
                .growthRate(entity.getGrowthRate())
                .bounceRate(entity.getBounceRate())
                .customerConcentration(entity.getCustomerConcentration())
                .build())
            .scoreBreakdown(ScoreBreakdown.builder()
                .volumeScore(entity.getVolumeScore())
                .volumeWeight(new BigDecimal("0.30"))
                .consistencyScore(entity.getConsistencyScore())
                .consistencyWeight(new BigDecimal("0.25"))
                .growthScore(entity.getGrowthScore())
                .growthWeight(new BigDecimal("0.15"))
                .bounceRateScore(entity.getBounceRateScore())
                .bounceRateWeight(new BigDecimal("0.15"))
                .concentrationScore(entity.getConcentrationScore())
                .concentrationWeight(new BigDecimal("0.15"))
                .build())
            .warnings(entity.getWarnings())
            .strengths(entity.getStrengths())
            .reportUrl(entity.getReportUrl())
            .build();
    }
}
