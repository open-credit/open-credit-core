package com.lumexpay.vortexa.credit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DTO containing all calculated financial and performance metrics
 * used in credit scoring.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialMetrics {

    // ==========================================
    // VOLUME METRICS
    // ==========================================

    private BigDecimal last3MonthsVolume;
    private BigDecimal last6MonthsVolume;
    private BigDecimal last12MonthsVolume;
    private BigDecimal averageMonthlyVolume;
    private BigDecimal averageTransactionValue;
    private Integer totalTransactionCount;
    private Integer successfulTransactionCount;
    private Integer failedTransactionCount;

    // ==========================================
    // CUSTOMER METRICS
    // ==========================================

    private Integer uniqueCustomerCount;
    private BigDecimal top10CustomerVolume;
    private BigDecimal customerConcentration;

    // ==========================================
    // MONTHLY BREAKDOWN
    // ==========================================

    @Builder.Default
    private List<MonthlyVolume> monthlyVolumes = new ArrayList<>();

    @Builder.Default
    private Map<String, BigDecimal> customerVolumes = Map.of();

    // ==========================================
    // PERFORMANCE METRICS
    // ==========================================

    private BigDecimal consistencyScore;
    private BigDecimal growthRate;
    private BigDecimal bounceRate;
    private BigDecimal previousPeriodVolume; // For growth calculation

    // ==========================================
    // SEASONALITY DETECTION
    // ==========================================

    private Boolean isSeasonalBusiness;
    private BigDecimal coefficientOfVariation;
    private String peakMonth;
    private String troughMonth;

    // ==========================================
    // FRAUD INDICATORS
    // ==========================================

    private Boolean hasSuddenVolumeSpike;
    private Boolean hasLowCustomerDiversity;
    private Boolean hasSinglePayerDominance;

    /**
     * Monthly volume breakdown
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyVolume {
        private String month; // YYYY-MM format
        private BigDecimal volume;
        private Integer transactionCount;
        private Integer uniqueCustomers;
    }
}
