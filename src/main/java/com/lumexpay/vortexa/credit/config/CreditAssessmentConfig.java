package com.lumexpay.vortexa.credit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Configuration properties for credit assessment parameters.
 */
@Configuration
@ConfigurationProperties(prefix = "credit.assessment")
@Data
public class CreditAssessmentConfig {

    // Minimum thresholds
    private BigDecimal minMonthlyVolume = new BigDecimal("25000");
    private int minTransactionCount = 20;
    private BigDecimal maxBounceRate = new BigDecimal("20");
    private int minTenureMonths = 3;

    // Scoring weights
    private Weights weights = new Weights();

    // Volume thresholds
    private VolumeThresholds volumeThresholds = new VolumeThresholds();

    // Risk thresholds
    private RiskThresholds riskThresholds = new RiskThresholds();

    // Eligibility settings
    private Eligibility eligibility = new Eligibility();

    // Tenure settings
    private Tenure tenure = new Tenure();

    // Interest rates
    private InterestRates interestRates = new InterestRates();

    @Data
    public static class Weights {
        private BigDecimal volume = new BigDecimal("0.30");
        private BigDecimal consistency = new BigDecimal("0.25");
        private BigDecimal growth = new BigDecimal("0.15");
        private BigDecimal bounceRate = new BigDecimal("0.15");
        private BigDecimal concentration = new BigDecimal("0.15");
    }

    @Data
    public static class VolumeThresholds {
        private BigDecimal excellent = new BigDecimal("500000");    // ₹5 lakh+
        private BigDecimal good = new BigDecimal("200000");         // ₹2-5 lakh
        private BigDecimal average = new BigDecimal("100000");      // ₹1-2 lakh
        private BigDecimal belowAverage = new BigDecimal("50000");  // ₹50K-1 lakh
        private BigDecimal low = new BigDecimal("25000");           // ₹25-50K
    }

    @Data
    public static class RiskThresholds {
        private int lowRiskMin = 80;      // Score 80-100 = LOW risk
        private int mediumRiskMin = 60;   // Score 60-79 = MEDIUM risk
        // Score 0-59 = HIGH risk (default)
    }

    @Data
    public static class Eligibility {
        private BigDecimal lowRiskMultiplier = new BigDecimal("0.30");
        private BigDecimal mediumRiskMultiplier = new BigDecimal("0.25");
        private BigDecimal highRiskMultiplier = new BigDecimal("0.15");
        private BigDecimal minLoanAmount = new BigDecimal("10000");
        private BigDecimal maxLoanAmount = new BigDecimal("5000000");
    }

    @Data
    public static class Tenure {
        private int lowRiskConsistent = 365;
        private int lowRisk = 180;
        private int mediumRisk = 90;
        private int highRisk = 30;
    }

    @Data
    public static class InterestRates {
        private BigDecimal lowRisk = new BigDecimal("18");
        private BigDecimal mediumRisk = new BigDecimal("24");
        private BigDecimal highRisk = new BigDecimal("30");
    }
    
    /**
     * Determine interest rate based on risk category.
     */
    public BigDecimal getInterestRateForRisk(String riskCategory) {
        return switch (riskCategory.toUpperCase()) {
            case "LOW" -> interestRates.getLowRisk();
            case "MEDIUM" -> interestRates.getMediumRisk();
            default -> interestRates.getHighRisk();
        };
    }
}