package com.lumexpay.vortexa.credit.rules.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Root model for scoring rules YAML.
 * Represents the complete rule configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoringRules {
    
    private String version;
    private String lastUpdated;
    private List<Maintainer> maintainers;
    private Metadata metadata;
    private Scoring scoring;
    private Map<String, RiskCategory> riskCategories;
    private Eligibility eligibility;
    private FraudDetection fraudDetection;
    private LoanParameters loanParameters;
    private Governance governance;
    private List<ChangelogEntry> changelog;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Maintainer {
        private String name;
        private String email;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {
        private String name;
        private String description;
        private List<String> targetUsers;
        private List<String> dataSources;
        private List<String> excludedFactors;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Scoring {
        private Map<String, ScoringComponent> components;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoringComponent {
        private BigDecimal weight;
        private String description;
        private String metric;
        private String unit;
        private String calculation;
        private BigDecimal minimumScore;
        private BigDecimal maximumScore;
        private List<ScoringTier> tiers;
        private List<Interpretation> interpretation;
        private SeasonalAdjustment seasonalAdjustment;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoringTier {
        private BigDecimal min;
        private BigDecimal max;
        private BigDecimal score;
        private String label;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Interpretation {
        private String cvRange;
        private String scoreRange;
        private String meaning;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeasonalAdjustment {
        private boolean enabled;
        private BigDecimal thresholdCv;
        private BigDecimal bonusIfSeasonal;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskCategory {
        private ScoreRange scoreRange;
        private String label;
        private String description;
        private String color;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreRange {
        private int min;
        private int max;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Eligibility {
        private String description;
        private List<EligibilityRule> rules;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EligibilityRule {
        private String id;
        private String name;
        private String description;
        private RuleCondition condition;
        private String failureMessage;
        private String recommendation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleCondition {
        private String metric;
        private String operator;
        private BigDecimal value;
        private String unit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FraudDetection {
        private String description;
        private List<FraudRule> rules;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FraudRule {
        private String id;
        private String name;
        private String description;
        private RuleCondition condition;
        private String severity;
        private String action;
        private String explanation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoanParameters {
        private String description;
        private LoanAmount amount;
        private LoanTenure tenure;
        private InterestRate interestRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoanAmount {
        private String description;
        private Map<String, RiskMultiplier> byRiskCategory;
        private Limits limits;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskMultiplier {
        private BigDecimal multiplier;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Limits {
        private BigDecimal minimum;
        private BigDecimal maximum;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoanTenure {
        private String description;
        private Map<String, TenureConfig> byRiskCategory;
        private ConsistencyAdjustment consistencyAdjustment;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenureConfig {
        private int maxDays;
        private int defaultDays;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsistencyAdjustment {
        private boolean enabled;
        private BigDecimal threshold;
        private BigDecimal reductionFactor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterestRate {
        private String description;
        private Map<String, RateConfig> byRiskCategory;
        private String regulatoryNote;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateConfig {
        private BigDecimal annualRate;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Governance {
        private String description;
        private List<ChangeProcessStep> changeProcess;
        private List<String> principles;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangeProcessStep {
        private int step;
        private String action;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangelogEntry {
        private String version;
        private String date;
        private List<String> changes;
        private List<String> contributors;
    }
}
