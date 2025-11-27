package com.lumexpay.vortexa.credit.service;

import com.lumexpay.vortexa.credit.config.CreditAssessmentConfig;
import com.lumexpay.vortexa.credit.dto.FinancialMetrics;
import com.lumexpay.vortexa.credit.model.CreditAssessment;
import com.lumexpay.vortexa.credit.model.CreditAssessment.RiskCategory;
import com.lumexpay.vortexa.credit.rules.RuleEngine;
import com.lumexpay.vortexa.credit.rules.RuleEngine.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for calculating credit scores and determining eligibility.
 * 
 * NOW POWERED BY OPENCREDIT RULE ENGINE
 * =====================================
 * All scoring rules are defined in human-readable YAML files:
 * - scoring-rules.yaml: Scoring methodology, weights, tiers
 * - eligibility-rules.yaml: Minimum requirements for loans
 * 
 * Benefits of rule-based approach:
 * - TRANSPARENT: Anyone can read and understand the rules
 * - AUDITABLE: Complete version history of all changes
 * - FAIR: Community can propose improvements
 * - INCLUSIVE: Designed for underserved populations
 * 
 * Rules Repository: https://github.com/opencredit/rules
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CreditScoringService {

    private final CreditAssessmentConfig config;
    private final RuleEngine ruleEngine;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * Calculate all scores and determine eligibility using the OpenCredit Rule Engine.
     *
     * @param metrics Calculated financial metrics
     * @param merchantId Merchant identifier
     * @return Credit assessment with scores and eligibility
     */
    public CreditAssessment calculateScores(FinancialMetrics metrics, String merchantId) {
        log.info("Calculating credit scores for merchant: {} using OpenCredit Rules v{}", 
            merchantId, ruleEngine.getRulesVersion());

        // Step 1: Check for fraud indicators using rule engine
        List<FraudIndicator> fraudIndicators = ruleEngine.checkFraudIndicators(metrics);
        int fraudIndicatorCount = fraudIndicators.size();
        
        if (!fraudIndicators.isEmpty()) {
            log.warn("Fraud indicators detected for {}: {}", merchantId, 
                fraudIndicators.stream().map(FraudIndicator::getName).toList());
        }

        // Step 2: Calculate credit score using rule engine
        ScoringResult scoringResult = ruleEngine.calculateScore(metrics);
        int creditScore = scoringResult.getCreditScore();

        // Step 3: Check eligibility using rule engine
        EligibilityResult eligibilityResult = ruleEngine.checkEligibility(metrics, fraudIndicatorCount);

        // Step 4: Determine risk category
        RiskCategory riskCategory = determineRiskCategory(creditScore);

        // Step 5: Calculate loan parameters using rule engine
        LoanParametersResult loanParams = ruleEngine.calculateLoanParameters(
            riskCategory.name(),
            metrics.getAverageMonthlyVolume() != null ? metrics.getAverageMonthlyVolume() : BigDecimal.ZERO,
            metrics.getConsistencyScore()
        );

        // Step 6: Determine final eligibility
        boolean isEligible = eligibilityResult.isEligible() && fraudIndicatorCount == 0;
        String ineligibilityReason = null;
        
        if (!isEligible) {
            if (fraudIndicatorCount > 0) {
                ineligibilityReason = "Suspicious transaction patterns detected";
            } else {
                ineligibilityReason = eligibilityResult.getFailureReason();
            }
        }

        // Step 7: Build warnings and strengths from rule engine results
        List<String> warnings = new ArrayList<>(scoringResult.getWarnings());
        List<String> strengths = new ArrayList<>(scoringResult.getStrengths());
        
        // Add fraud-related warnings
        for (FraudIndicator indicator : fraudIndicators) {
            warnings.add(indicator.getName());
        }

        // Step 8: Build complete assessment
        CreditAssessment assessment = CreditAssessment.builder()
            .merchantId(merchantId)
            .assessmentDate(LocalDateTime.now())
            
            // Credit score and risk
            .creditScore(creditScore)
            .riskCategory(riskCategory)
            
            // Financial metrics
            .last3MonthsVolume(metrics.getLast3MonthsVolume())
            .last6MonthsVolume(metrics.getLast6MonthsVolume())
            .last12MonthsVolume(metrics.getLast12MonthsVolume())
            .averageMonthlyVolume(metrics.getAverageMonthlyVolume())
            .averageTransactionValue(metrics.getAverageTransactionValue())
            .transactionCount(metrics.getTotalTransactionCount())
            .uniqueCustomerCount(metrics.getUniqueCustomerCount())
            
            // Performance metrics
            .consistencyScore(metrics.getConsistencyScore())
            .growthRate(metrics.getGrowthRate())
            .bounceRate(metrics.getBounceRate())
            .customerConcentration(metrics.getCustomerConcentration())
            
            // Component scores from rule engine
            .volumeScore(getComponentScore(scoringResult, "volume"))
            .growthScore(getComponentScore(scoringResult, "growth"))
            .bounceRateScore(getComponentScore(scoringResult, "bounce_rate"))
            .concentrationScore(getComponentScore(scoringResult, "concentration"))
            
            // Eligibility
            .isEligible(isEligible)
            .ineligibilityReason(ineligibilityReason)
            
            // Loan parameters
            .eligibleLoanAmount(isEligible ? loanParams.getEligibleAmount() : BigDecimal.ZERO)
            .maxTenureDays(isEligible ? loanParams.getMaxTenureDays() : 0)
            .recommendedInterestRate(loanParams.getInterestRateAnnual())
            
            // Insights
            .warnings(warnings)
            .strengths(strengths)
            
            .build();

        log.info("Assessment complete: Merchant={}, Score={}, Risk={}, Eligible={}, Rules=v{}", 
            merchantId, creditScore, riskCategory.name(), isEligible, ruleEngine.getRulesVersion());

        return assessment;
    }

    /**
     * Get component score from scoring result.
     */
    private BigDecimal getComponentScore(ScoringResult result, String componentName) {
        if (result.getComponentScores() == null) {
            return BigDecimal.ZERO;
        }
        ComponentScore comp = result.getComponentScores().get(componentName);
        return comp != null ? comp.getScore() : BigDecimal.ZERO;
    }

    /**
     * Get current rules version for transparency.
     */
    public String getRulesVersion() {
        return ruleEngine.getRulesVersion();
    }

    // ========================================================================
    // COMPONENT SCORE CALCULATIONS (Legacy - now primarily uses RuleEngine)
    // ========================================================================

    /**
     * Calculate volume score based on average monthly volume.
     * Tiers defined in scoring-rules.yaml
     */
    public BigDecimal calculateVolumeScore(BigDecimal avgMonthlyVolume) {
        if (avgMonthlyVolume == null) {
            return BigDecimal.ZERO;
        }

        // Use rule engine if available, otherwise fallback to hardcoded
        try {
            ScoringResult result = ruleEngine.calculateScore(
                FinancialMetrics.builder().averageMonthlyVolume(avgMonthlyVolume).build()
            );
            return getComponentScore(result, "volume");
        } catch (Exception e) {
            // Fallback to legacy calculation
            return calculateVolumeScoreLegacy(avgMonthlyVolume);
        }
    }

    private BigDecimal calculateVolumeScoreLegacy(BigDecimal avgMonthlyVolume) {
        CreditAssessmentConfig.VolumeThresholds thresholds = config.getVolumeThresholds();
        
        if (avgMonthlyVolume.compareTo(thresholds.getExcellent()) >= 0) {
            return HUNDRED;
        } else if (avgMonthlyVolume.compareTo(thresholds.getGood()) >= 0) {
            return new BigDecimal("80");
        } else if (avgMonthlyVolume.compareTo(thresholds.getAverage()) >= 0) {
            return new BigDecimal("60");
        } else if (avgMonthlyVolume.compareTo(thresholds.getBelowAverage()) >= 0) {
            return new BigDecimal("40");
        }
        return new BigDecimal("20");
    }

    /**
     * Calculate growth score based on growth rate percentage.
     * Tiers defined in scoring-rules.yaml
     */
    public BigDecimal calculateGrowthScore(BigDecimal growthRate) {
        if (growthRate == null) {
            return new BigDecimal("50");
        }

        if (growthRate.compareTo(new BigDecimal("30")) >= 0) {
            return HUNDRED;
        } else if (growthRate.compareTo(new BigDecimal("15")) >= 0) {
            return new BigDecimal("85");
        } else if (growthRate.compareTo(BigDecimal.ZERO) >= 0) {
            return new BigDecimal("70");
        } else if (growthRate.compareTo(new BigDecimal("-10")) >= 0) {
            return new BigDecimal("50");
        } else if (growthRate.compareTo(new BigDecimal("-25")) >= 0) {
            return new BigDecimal("30");
        }
        return new BigDecimal("15");
    }

    /**
     * Calculate bounce rate score (lower bounce rate = higher score).
     * Tiers defined in scoring-rules.yaml
     */
    public BigDecimal calculateBounceRateScore(BigDecimal bounceRate) {
        if (bounceRate == null) {
            return new BigDecimal("70");
        }

        if (bounceRate.compareTo(new BigDecimal("3")) <= 0) {
            return HUNDRED;
        } else if (bounceRate.compareTo(new BigDecimal("5")) <= 0) {
            return new BigDecimal("85");
        } else if (bounceRate.compareTo(new BigDecimal("10")) <= 0) {
            return new BigDecimal("70");
        } else if (bounceRate.compareTo(new BigDecimal("15")) <= 0) {
            return new BigDecimal("50");
        } else if (bounceRate.compareTo(new BigDecimal("20")) <= 0) {
            return new BigDecimal("30");
        }
        return new BigDecimal("10");
    }

    /**
     * Calculate concentration score (lower concentration = higher score).
     * Tiers defined in scoring-rules.yaml
     */
    public BigDecimal calculateConcentrationScore(BigDecimal concentration) {
        if (concentration == null) {
            return new BigDecimal("70");
        }

        if (concentration.compareTo(new BigDecimal("20")) <= 0) {
            return HUNDRED;
        } else if (concentration.compareTo(new BigDecimal("30")) <= 0) {
            return new BigDecimal("85");
        } else if (concentration.compareTo(new BigDecimal("50")) <= 0) {
            return new BigDecimal("65");
        } else if (concentration.compareTo(new BigDecimal("70")) <= 0) {
            return new BigDecimal("45");
        }
        return new BigDecimal("25");
    }

    /**
     * Calculate final weighted score from components.
     * Weights defined in scoring-rules.yaml
     */
    public BigDecimal calculateWeightedScore(
            BigDecimal volumeScore,
            BigDecimal consistencyScore,
            BigDecimal growthScore,
            BigDecimal bounceRateScore,
            BigDecimal concentrationScore) {

        CreditAssessmentConfig.Weights weights = config.getWeights();

        BigDecimal weightedVolume = volumeScore.multiply(weights.getVolume());
        BigDecimal weightedConsistency = consistencyScore.multiply(weights.getConsistency());
        BigDecimal weightedGrowth = growthScore.multiply(weights.getGrowth());
        BigDecimal weightedBounce = bounceRateScore.multiply(weights.getBounceRate());
        BigDecimal weightedConcentration = concentrationScore.multiply(weights.getConcentration());

        return weightedVolume
            .add(weightedConsistency)
            .add(weightedGrowth)
            .add(weightedBounce)
            .add(weightedConcentration)
            .setScale(2, RoundingMode.HALF_UP);
    }

    // ========================================================================
    // RISK & ELIGIBILITY
    // ========================================================================

    /**
     * Determine risk category based on credit score.
     * Thresholds defined in scoring-rules.yaml
     */
    public RiskCategory determineRiskCategory(int creditScore) {
        CreditAssessmentConfig.RiskThresholds thresholds = config.getRiskThresholds();
        
        if (creditScore >= thresholds.getLowRiskMin()) {
            return RiskCategory.LOW;
        } else if (creditScore >= thresholds.getMediumRiskMin()) {
            return RiskCategory.MEDIUM;
        }
        return RiskCategory.HIGH;
    }

    /**
     * Check eligibility based on metrics.
     * Rules defined in eligibility-rules.yaml
     */
    public EligibilityCheckResult checkEligibility(FinancialMetrics metrics, int creditScore) {
        List<String> failureReasons = new ArrayList<>();
        
        // Use rule engine for eligibility check
        RuleEngine.EligibilityResult result = ruleEngine.checkEligibility(metrics, 0);
        
        if (!result.isEligible()) {
            return new EligibilityCheckResult(false, result.getFailureReason());
        }

        return new EligibilityCheckResult(true, null);
    }

    // ========================================================================
    // LOAN PARAMETERS
    // ========================================================================

    /**
     * Calculate eligible loan amount based on risk category.
     * Rules defined in scoring-rules.yaml
     */
    public BigDecimal calculateEligibleLoanAmount(RiskCategory riskCategory, BigDecimal avgMonthlyVolume) {
        LoanParametersResult params = ruleEngine.calculateLoanParameters(
            riskCategory.name(),
            avgMonthlyVolume,
            null
        );
        return params.getEligibleAmount();
    }

    /**
     * Calculate maximum loan tenure.
     * Rules defined in scoring-rules.yaml
     */
    public int calculateMaxTenure(RiskCategory riskCategory, BigDecimal consistencyScore) {
        LoanParametersResult params = ruleEngine.calculateLoanParameters(
            riskCategory.name(),
            BigDecimal.ZERO,
            consistencyScore
        );
        return params.getMaxTenureDays();
    }

    /**
     * Get recommended interest rate.
     * Rules defined in scoring-rules.yaml
     */
    public BigDecimal getRecommendedInterestRate(RiskCategory riskCategory) {
        LoanParametersResult params = ruleEngine.calculateLoanParameters(
            riskCategory.name(),
            BigDecimal.ZERO,
            null
        );
        return params.getInterestRateAnnual();
    }

    /**
     * Determine interest rate based on risk category.
     * Alias for getRecommendedInterestRate for backward compatibility.
     */
    public BigDecimal determineInterestRate(RiskCategory riskCategory) {
        return getRecommendedInterestRate(riskCategory);
    }

    // ========================================================================
    // HELPER CLASSES
    // ========================================================================

    /**
     * Result of eligibility check.
     */
    public record EligibilityCheckResult(boolean isEligible, String reason) {}
}