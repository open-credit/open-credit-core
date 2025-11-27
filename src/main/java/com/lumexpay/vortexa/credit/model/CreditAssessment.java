package com.lumexpay.vortexa.credit.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a complete credit assessment for a merchant.
 * This is the primary output of the credit assessment engine.
 */
@Entity
@Table(name = "credit_assessments", indexes = {
    @Index(name = "idx_merchant_id", columnList = "merchantId"),
    @Index(name = "idx_assessment_date", columnList = "assessmentDate"),
    @Index(name = "idx_credit_score", columnList = "creditScore")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID assessmentId;

    @Column(nullable = false)
    private String merchantId;

    @Column(nullable = false)
    private LocalDateTime assessmentDate;

    // ==========================================
    // FINANCIAL METRICS
    // ==========================================

    @Column(precision = 15, scale = 2)
    private BigDecimal last3MonthsVolume;

    @Column(precision = 15, scale = 2)
    private BigDecimal last6MonthsVolume;

    @Column(precision = 15, scale = 2)
    private BigDecimal last12MonthsVolume;

    @Column(precision = 15, scale = 2)
    private BigDecimal averageMonthlyVolume;

    @Column(precision = 15, scale = 2)
    private BigDecimal averageTransactionValue;

    private Integer transactionCount;

    private Integer uniqueCustomerCount;

    // ==========================================
    // PERFORMANCE METRICS
    // ==========================================

    @Column(precision = 5, scale = 2)
    private BigDecimal consistencyScore;

    @Column(precision = 8, scale = 4)
    private BigDecimal growthRate;

    @Column(precision = 5, scale = 2)
    private BigDecimal bounceRate;

    @Column(precision = 5, scale = 2)
    private BigDecimal customerConcentration;

    // ==========================================
    // CREDIT SCORE & RISK
    // ==========================================

    @Column(nullable = false)
    private Integer creditScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskCategory riskCategory;

    // ==========================================
    // ELIGIBILITY
    // ==========================================

    @Column(precision = 15, scale = 2)
    private BigDecimal eligibleLoanAmount;

    private Integer maxTenureDays;

    @Column(precision = 5, scale = 2)
    private BigDecimal recommendedInterestRate;

    @Column(nullable = false)
    private Boolean isEligible;

    @Column(length = 500)
    private String ineligibilityReason;

    // ==========================================
    // COMPONENT SCORES
    // ==========================================

    @Column(precision = 5, scale = 2)
    private BigDecimal volumeScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal growthScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal bounceRateScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal concentrationScore;

    // ==========================================
    // WARNINGS & STRENGTHS
    // ==========================================

    @ElementCollection
    @CollectionTable(name = "credit_assessment_warnings",
        joinColumns = @JoinColumn(name = "assessment_id"))
    @Column(name = "warning")
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "credit_assessment_strengths",
        joinColumns = @JoinColumn(name = "assessment_id"))
    @Column(name = "strength")
    @Builder.Default
    private List<String> strengths = new ArrayList<>();

    // ==========================================
    // REPORT
    // ==========================================

    @Column(length = 500)
    private String reportUrl;

    @Column(length = 100)
    private String reportFileName;

    // ==========================================
    // METADATA
    // ==========================================

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Long version;

    /**
     * Risk category enum
     */
    public enum RiskCategory {
        LOW("Low Risk - Excellent Credit"),
        MEDIUM("Medium Risk - Good Credit"),
        HIGH("High Risk - Poor Credit");

        private final String description;

        RiskCategory(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (assessmentDate == null) {
            assessmentDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}