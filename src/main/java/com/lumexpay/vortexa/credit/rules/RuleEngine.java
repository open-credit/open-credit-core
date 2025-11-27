package com.lumexpay.vortexa.credit.rules;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.lumexpay.vortexa.credit.dto.FinancialMetrics;
import com.lumexpay.vortexa.credit.rules.model.ScoringRules;
import com.lumexpay.vortexa.credit.rules.model.ScoringRules.*;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * OpenCredit Rule Engine
 * 
 * A transparent, YAML-based rule engine for credit scoring.
 * Rules are defined in human-readable YAML files that can be:
 * - Audited by anyone
 * - Version controlled
 * - Community contributed
 * - Easily understood by non-programmers
 * 
 * This engine reads rules from YAML files and executes them against
 * merchant financial metrics to produce credit scores and eligibility decisions.
 */
@Service
@Slf4j
public class RuleEngine {

    @Value("${rules.path:rules/scoring-rules.yaml}")
    private String rulesPath;

    @Value("${rules.eligibility-path:rules/eligibility-rules.yaml}")
    private String eligibilityRulesPath;

    private ScoringRules scoringRules;
    private final ObjectMapper yamlMapper;

    public RuleEngine() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        this.yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Load rules from YAML on startup.
     */
    @PostConstruct
    public void loadRules() {
        try {
            Resource resource = new ClassPathResource(rulesPath);
            try (InputStream is = resource.getInputStream()) {
                this.scoringRules = yamlMapper.readValue(is, ScoringRules.class);
                log.info("✓ Loaded OpenCredit scoring rules v{}", scoringRules.getVersion());
                logRulesSummary();
            }
        } catch (IOException e) {
            log.error("Failed to load scoring rules from {}: {}", rulesPath, e.getMessage());
            // Load default embedded rules
            this.scoringRules = createDefaultRules();
            log.warn("Using default embedded rules");
        }
    }

    /**
     * Reload rules from YAML (for hot-reloading).
     */
    public void reloadRules() {
        loadRules();
        log.info("Rules reloaded successfully");
    }

    /**
     * Get the current rules version.
     */
    public String getRulesVersion() {
        return scoringRules != null ? scoringRules.getVersion() : "unknown";
    }

    /**
     * Get the full rules for inspection/display.
     */
    public ScoringRules getRules() {
        return scoringRules;
    }

    // ========================================================================
    // SCORING METHODS
    // ========================================================================

    /**
     * Calculate the complete credit score using YAML-defined rules.
     */
    public ScoringResult calculateScore(FinancialMetrics metrics) {
        log.debug("Calculating credit score using rules v{}", getRulesVersion());

        Map<String, ComponentScore> componentScores = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        List<String> strengths = new ArrayList<>();
        BigDecimal totalWeightedScore = BigDecimal.ZERO;

        // Calculate each component score based on rules
        for (Map.Entry<String, ScoringComponent> entry : scoringRules.getScoring().getComponents().entrySet()) {
            String componentName = entry.getKey();
            ScoringComponent component = entry.getValue();

            ComponentScore score = calculateComponentScore(componentName, component, metrics);
            componentScores.put(componentName, score);

            // Add weighted score
            BigDecimal weightedScore = score.getScore()
                .multiply(component.getWeight())
                .setScale(2, RoundingMode.HALF_UP);
            totalWeightedScore = totalWeightedScore.add(weightedScore);

            // Collect warnings and strengths
            if (score.getWarning() != null) {
                warnings.add(score.getWarning());
            }
            if (score.getStrength() != null) {
                strengths.add(score.getStrength());
            }
        }

        int finalScore = totalWeightedScore.setScale(0, RoundingMode.HALF_UP).intValue();
        finalScore = Math.max(0, Math.min(100, finalScore)); // Clamp to 0-100

        // Determine risk category
        String riskCategory = determineRiskCategory(finalScore);

        return ScoringResult.builder()
            .creditScore(finalScore)
            .riskCategory(riskCategory)
            .componentScores(componentScores)
            .warnings(warnings)
            .strengths(strengths)
            .rulesVersion(getRulesVersion())
            .build();
    }

    /**
     * Calculate score for a single component.
     */
    private ComponentScore calculateComponentScore(String name, ScoringComponent component, FinancialMetrics metrics) {
        BigDecimal metricValue = getMetricValue(component.getMetric(), metrics);
        BigDecimal score;
        String label = "Unknown";
        String warning = null;
        String strength = null;

        // Handle special calculation-based components (like consistency)
        if (component.getCalculation() != null && component.getCalculation().contains("coefficient_of_variation")) {
            // Consistency score = 100 - (CV * 100)
            BigDecimal cv = metrics.getCoefficientOfVariation() != null ? 
                metrics.getCoefficientOfVariation() : BigDecimal.ZERO;
            score = BigDecimal.valueOf(100).subtract(cv.multiply(BigDecimal.valueOf(100)));
            score = score.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));

            // Check for seasonal adjustment
            if (component.getSeasonalAdjustment() != null && 
                component.getSeasonalAdjustment().isEnabled() &&
                Boolean.TRUE.equals(metrics.getIsSeasonalBusiness())) {
                score = score.add(component.getSeasonalAdjustment().getBonusIfSeasonal());
                score = score.min(BigDecimal.valueOf(100));
            }

            label = score.compareTo(BigDecimal.valueOf(80)) >= 0 ? "Stable" :
                    score.compareTo(BigDecimal.valueOf(60)) >= 0 ? "Moderate" :
                    score.compareTo(BigDecimal.valueOf(40)) >= 0 ? "Variable" : "Volatile";

            if (score.compareTo(BigDecimal.valueOf(50)) < 0) {
                warning = "Inconsistent monthly volumes";
            } else if (score.compareTo(BigDecimal.valueOf(80)) >= 0) {
                strength = "Very consistent business";
            }

        } else if (component.getTiers() != null && !component.getTiers().isEmpty()) {
            // Tier-based scoring
            ScoringTier matchedTier = findMatchingTier(metricValue, component.getTiers(), name);
            score = matchedTier.getScore();
            label = matchedTier.getLabel();

            // Generate warnings/strengths based on tier
            if ("volume".equals(name)) {
                if (score.compareTo(BigDecimal.valueOf(40)) <= 0) {
                    warning = "Low transaction volume";
                } else if (score.compareTo(BigDecimal.valueOf(80)) >= 0) {
                    strength = "Strong transaction volume";
                }
            } else if ("growth".equals(name)) {
                if (metricValue.compareTo(BigDecimal.ZERO) < 0) {
                    warning = "Business volume is declining";
                } else if (score.compareTo(BigDecimal.valueOf(85)) >= 0) {
                    strength = "Strong growth trajectory";
                }
            } else if ("bounce_rate".equals(name)) {
                if (score.compareTo(BigDecimal.valueOf(50)) <= 0) {
                    warning = "High transaction failure rate";
                } else if (score.compareTo(BigDecimal.valueOf(85)) >= 0) {
                    strength = "Excellent transaction success rate";
                }
            } else if ("concentration".equals(name)) {
                if (score.compareTo(BigDecimal.valueOf(45)) <= 0) {
                    warning = "High customer concentration risk";
                } else if (score.compareTo(BigDecimal.valueOf(85)) >= 0) {
                    strength = "Well-diversified customer base";
                }
            }
        } else {
            score = BigDecimal.ZERO;
        }

        return ComponentScore.builder()
            .name(name)
            .score(score)
            .weight(component.getWeight())
            .metricValue(metricValue)
            .label(label)
            .description(component.getDescription())
            .warning(warning)
            .strength(strength)
            .build();
    }

    /**
     * Find matching tier for a metric value.
     */
    private ScoringTier findMatchingTier(BigDecimal value, List<ScoringTier> tiers, String componentName) {
        // For bounce_rate and concentration, lower is better (reverse tiers)
        boolean lowerIsBetter = "bounce_rate".equals(componentName) || "concentration".equals(componentName);

        for (ScoringTier tier : tiers) {
            BigDecimal min = tier.getMin();
            BigDecimal max = tier.getMax();

            boolean matchesMin = min == null || value.compareTo(min) >= 0;
            boolean matchesMax = max == null || value.compareTo(max) < 0;

            if (matchesMin && matchesMax) {
                return tier;
            }
        }

        // Return first/last tier as fallback
        return tiers.get(tiers.size() - 1);
    }

    /**
     * Get metric value from FinancialMetrics based on metric name.
     */
    private BigDecimal getMetricValue(String metricName, FinancialMetrics metrics) {
        if (metrics == null || metricName == null) {
            return BigDecimal.ZERO;
        }

        return switch (metricName) {
            case "average_monthly_volume" -> metrics.getAverageMonthlyVolume() != null ? 
                metrics.getAverageMonthlyVolume() : BigDecimal.ZERO;
            case "coefficient_of_variation" -> metrics.getCoefficientOfVariation() != null ?
                metrics.getCoefficientOfVariation() : BigDecimal.ZERO;
            case "growth_rate_percentage" -> metrics.getGrowthRate() != null ?
                metrics.getGrowthRate() : BigDecimal.ZERO;
            case "bounce_rate_percentage" -> metrics.getBounceRate() != null ?
                metrics.getBounceRate() : BigDecimal.ZERO;
            case "top_10_customer_concentration_percentage" -> metrics.getCustomerConcentration() != null ?
                metrics.getCustomerConcentration() : BigDecimal.ZERO;
            case "consistency_score" -> metrics.getConsistencyScore() != null ?
                metrics.getConsistencyScore() : BigDecimal.ZERO;
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * Determine risk category based on score.
     */
    private String determineRiskCategory(int score) {
        if (scoringRules.getRiskCategories() != null) {
            for (Map.Entry<String, RiskCategory> entry : scoringRules.getRiskCategories().entrySet()) {
                RiskCategory cat = entry.getValue();
                if (score >= cat.getScoreRange().getMin() && score <= cat.getScoreRange().getMax()) {
                    return entry.getKey().toUpperCase().replace("_RISK", "");
                }
            }
        }
        // Default classification
        if (score >= 80) return "LOW";
        if (score >= 60) return "MEDIUM";
        return "HIGH";
    }

    // ========================================================================
    // ELIGIBILITY METHODS
    // ========================================================================

    /**
     * Check eligibility using YAML-defined rules.
     */
    public EligibilityResult checkEligibility(FinancialMetrics metrics, int fraudIndicatorCount) {
        if (scoringRules.getEligibility() == null || scoringRules.getEligibility().getRules() == null) {
            return EligibilityResult.builder()
                .eligible(true)
                .rulesChecked(0)
                .rulesPassed(0)
                .build();
        }

        List<RuleCheckResult> results = new ArrayList<>();
        boolean allPassed = true;
        String failureReason = null;
        String recommendation = null;

        for (EligibilityRule rule : scoringRules.getEligibility().getRules()) {
            RuleCheckResult result = evaluateEligibilityRule(rule, metrics, fraudIndicatorCount);
            results.add(result);

            if (!result.isPassed()) {
                allPassed = false;
                if (failureReason == null) {
                    failureReason = rule.getFailureMessage();
                    recommendation = rule.getRecommendation();
                }
            }
        }

        return EligibilityResult.builder()
            .eligible(allPassed)
            .failureReason(failureReason)
            .recommendation(recommendation)
            .ruleResults(results)
            .rulesChecked(results.size())
            .rulesPassed((int) results.stream().filter(RuleCheckResult::isPassed).count())
            .rulesVersion(getRulesVersion())
            .build();
    }

    /**
     * Evaluate a single eligibility rule.
     */
    private RuleCheckResult evaluateEligibilityRule(EligibilityRule rule, FinancialMetrics metrics, int fraudIndicatorCount) {
        RuleCondition condition = rule.getCondition();
        BigDecimal actualValue = getEligibilityMetricValue(condition.getMetric(), metrics, fraudIndicatorCount);
        BigDecimal threshold = condition.getValue();
        boolean passed = evaluateCondition(actualValue, condition.getOperator(), threshold);

        return RuleCheckResult.builder()
            .ruleId(rule.getId())
            .ruleName(rule.getName())
            .passed(passed)
            .actualValue(actualValue)
            .threshold(threshold)
            .operator(condition.getOperator())
            .build();
    }

    /**
     * Get metric value for eligibility check.
     */
    private BigDecimal getEligibilityMetricValue(String metricName, FinancialMetrics metrics, int fraudIndicatorCount) {
        return switch (metricName) {
            case "average_monthly_volume" -> metrics.getAverageMonthlyVolume() != null ?
                metrics.getAverageMonthlyVolume() : BigDecimal.ZERO;
            case "total_transaction_count" -> BigDecimal.valueOf(
                metrics.getTotalTransactionCount() != null ? metrics.getTotalTransactionCount() : 0);
            case "bounce_rate" -> metrics.getBounceRate() != null ?
                metrics.getBounceRate() : BigDecimal.ZERO;
            case "business_tenure_months" -> BigDecimal.valueOf(
                metrics.getMonthlyVolumes() != null ? metrics.getMonthlyVolumes().size() : 0);
            case "fraud_indicators" -> BigDecimal.valueOf(fraudIndicatorCount);
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * Evaluate a condition (actual operator threshold).
     */
    private boolean evaluateCondition(BigDecimal actual, String operator, BigDecimal threshold) {
        if (actual == null || threshold == null) {
            return false;
        }

        return switch (operator) {
            case ">=", "GREATER_THAN_OR_EQUAL" -> actual.compareTo(threshold) >= 0;
            case ">", "GREATER_THAN" -> actual.compareTo(threshold) > 0;
            case "<=", "LESS_THAN_OR_EQUAL" -> actual.compareTo(threshold) <= 0;
            case "<", "LESS_THAN" -> actual.compareTo(threshold) < 0;
            case "==", "EQUAL" -> actual.compareTo(threshold) == 0;
            case "!=", "NOT_EQUAL" -> actual.compareTo(threshold) != 0;
            default -> false;
        };
    }

    // ========================================================================
    // LOAN PARAMETER METHODS
    // ========================================================================

    /**
     * Calculate loan parameters based on rules.
     */
    public LoanParametersResult calculateLoanParameters(String riskCategory, BigDecimal avgMonthlyVolume, BigDecimal consistencyScore) {
        LoanParameters params = scoringRules.getLoanParameters();
        if (params == null) {
            return getDefaultLoanParameters(riskCategory, avgMonthlyVolume);
        }

        String riskKey = riskCategory.toLowerCase() + "_risk";

        // Calculate loan amount
        BigDecimal multiplier = BigDecimal.valueOf(0.25); // default
        if (params.getAmount() != null && params.getAmount().getByRiskCategory() != null) {
            RiskMultiplier rm = params.getAmount().getByRiskCategory().get(riskKey);
            if (rm != null) {
                multiplier = rm.getMultiplier();
            }
        }

        BigDecimal loanAmount = avgMonthlyVolume.multiply(multiplier);

        // Apply limits
        if (params.getAmount() != null && params.getAmount().getLimits() != null) {
            Limits limits = params.getAmount().getLimits();
            if (limits.getMinimum() != null) {
                loanAmount = loanAmount.max(limits.getMinimum());
            }
            if (limits.getMaximum() != null) {
                loanAmount = loanAmount.min(limits.getMaximum());
            }
        }

        // Calculate tenure
        int maxTenureDays = 90; // default
        if (params.getTenure() != null && params.getTenure().getByRiskCategory() != null) {
            TenureConfig tc = params.getTenure().getByRiskCategory().get(riskKey);
            if (tc != null) {
                maxTenureDays = tc.getMaxDays();
            }
        }

        // Apply consistency adjustment
        if (params.getTenure() != null && params.getTenure().getConsistencyAdjustment() != null) {
            ConsistencyAdjustment adj = params.getTenure().getConsistencyAdjustment();
            if (adj.isEnabled() && consistencyScore != null && 
                consistencyScore.compareTo(adj.getThreshold()) < 0) {
                maxTenureDays = (int) (maxTenureDays * adj.getReductionFactor().doubleValue());
            }
        }

        // Calculate interest rate
        BigDecimal interestRate = BigDecimal.valueOf(24); // default
        if (params.getInterestRate() != null && params.getInterestRate().getByRiskCategory() != null) {
            RateConfig rc = params.getInterestRate().getByRiskCategory().get(riskKey);
            if (rc != null) {
                interestRate = rc.getAnnualRate();
            }
        }

        return LoanParametersResult.builder()
            .eligibleAmount(loanAmount.setScale(0, RoundingMode.HALF_UP))
            .maxTenureDays(maxTenureDays)
            .interestRateAnnual(interestRate)
            .amountMultiplier(multiplier)
            .build();
    }

    private LoanParametersResult getDefaultLoanParameters(String riskCategory, BigDecimal avgMonthlyVolume) {
        BigDecimal multiplier = switch (riskCategory) {
            case "LOW" -> BigDecimal.valueOf(0.30);
            case "MEDIUM" -> BigDecimal.valueOf(0.25);
            default -> BigDecimal.valueOf(0.15);
        };

        int tenure = switch (riskCategory) {
            case "LOW" -> 365;
            case "MEDIUM" -> 90;
            default -> 30;
        };

        BigDecimal rate = switch (riskCategory) {
            case "LOW" -> BigDecimal.valueOf(18);
            case "MEDIUM" -> BigDecimal.valueOf(24);
            default -> BigDecimal.valueOf(30);
        };

        return LoanParametersResult.builder()
            .eligibleAmount(avgMonthlyVolume.multiply(multiplier).setScale(0, RoundingMode.HALF_UP))
            .maxTenureDays(tenure)
            .interestRateAnnual(rate)
            .amountMultiplier(multiplier)
            .build();
    }

    // ========================================================================
    // FRAUD DETECTION
    // ========================================================================

    /**
     * Check for fraud indicators based on rules.
     */
    public List<FraudIndicator> checkFraudIndicators(FinancialMetrics metrics) {
        List<FraudIndicator> indicators = new ArrayList<>();

        if (scoringRules.getFraudDetection() == null || scoringRules.getFraudDetection().getRules() == null) {
            return indicators;
        }

        for (FraudRule rule : scoringRules.getFraudDetection().getRules()) {
            BigDecimal value = getFraudMetricValue(rule.getCondition().getMetric(), metrics);
            boolean triggered = evaluateCondition(value, rule.getCondition().getOperator(), rule.getCondition().getValue());

            if (triggered) {
                indicators.add(FraudIndicator.builder()
                    .ruleId(rule.getId())
                    .name(rule.getName())
                    .severity(rule.getSeverity())
                    .action(rule.getAction())
                    .explanation(rule.getExplanation())
                    .actualValue(value)
                    .threshold(rule.getCondition().getValue())
                    .build());
            }
        }

        return indicators;
    }

    private BigDecimal getFraudMetricValue(String metricName, FinancialMetrics metrics) {
        // These would need to be calculated in MetricsCalculationService
        return switch (metricName) {
            case "volume_spike_percentage" -> BigDecimal.ZERO; // TODO: implement
            case "unique_customer_count" -> BigDecimal.valueOf(
                metrics.getUniqueCustomerCount() != null ? metrics.getUniqueCustomerCount() : 0);
            case "top_customer_percentage" -> metrics.getCustomerConcentration() != null ?
                metrics.getCustomerConcentration() : BigDecimal.ZERO;
            default -> BigDecimal.ZERO;
        };
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    private void logRulesSummary() {
        if (scoringRules.getScoring() != null && scoringRules.getScoring().getComponents() != null) {
            log.info("  → {} scoring components loaded", scoringRules.getScoring().getComponents().size());
            scoringRules.getScoring().getComponents().forEach((name, comp) -> 
                log.debug("    - {}: weight={}", name, comp.getWeight()));
        }
        if (scoringRules.getEligibility() != null && scoringRules.getEligibility().getRules() != null) {
            log.info("  → {} eligibility rules loaded", scoringRules.getEligibility().getRules().size());
        }
        if (scoringRules.getFraudDetection() != null && scoringRules.getFraudDetection().getRules() != null) {
            log.info("  → {} fraud detection rules loaded", scoringRules.getFraudDetection().getRules().size());
        }
    }

    /**
     * Create default rules if YAML fails to load.
     */
    private ScoringRules createDefaultRules() {
        // Minimal default rules for fallback
        Map<String, ScoringComponent> components = new HashMap<>();

        components.put("volume", ScoringComponent.builder()
            .weight(BigDecimal.valueOf(0.30))
            .metric("average_monthly_volume")
            .tiers(List.of(
                ScoringTier.builder().min(BigDecimal.valueOf(500000)).score(BigDecimal.valueOf(100)).label("Excellent").build(),
                ScoringTier.builder().min(BigDecimal.valueOf(200000)).max(BigDecimal.valueOf(500000)).score(BigDecimal.valueOf(80)).label("Good").build(),
                ScoringTier.builder().min(BigDecimal.valueOf(100000)).max(BigDecimal.valueOf(200000)).score(BigDecimal.valueOf(60)).label("Average").build(),
                ScoringTier.builder().min(BigDecimal.valueOf(50000)).max(BigDecimal.valueOf(100000)).score(BigDecimal.valueOf(40)).label("Below Average").build(),
                ScoringTier.builder().min(BigDecimal.ZERO).max(BigDecimal.valueOf(50000)).score(BigDecimal.valueOf(20)).label("Low").build()
            ))
            .build());

        return ScoringRules.builder()
            .version("1.0.0-default")
            .scoring(Scoring.builder().components(components).build())
            .build();
    }

    // ========================================================================
    // RESULT MODELS
    // ========================================================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoringResult {
        private int creditScore;
        private String riskCategory;
        private Map<String, ComponentScore> componentScores;
        private List<String> warnings;
        private List<String> strengths;
        private String rulesVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentScore {
        private String name;
        private BigDecimal score;
        private BigDecimal weight;
        private BigDecimal metricValue;
        private String label;
        private String description;
        private String warning;
        private String strength;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EligibilityResult {
        private boolean eligible;
        private String failureReason;
        private String recommendation;
        private List<RuleCheckResult> ruleResults;
        private int rulesChecked;
        private int rulesPassed;
        private String rulesVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleCheckResult {
        private String ruleId;
        private String ruleName;
        private boolean passed;
        private BigDecimal actualValue;
        private BigDecimal threshold;
        private String operator;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoanParametersResult {
        private BigDecimal eligibleAmount;
        private int maxTenureDays;
        private BigDecimal interestRateAnnual;
        private BigDecimal amountMultiplier;
        private BigDecimal volumeMultiplier;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FraudIndicator {
        private String ruleId;
        private String name;
        private String severity;
        private String action;
        private String explanation;
        private BigDecimal actualValue;
        private BigDecimal threshold;
    }
}