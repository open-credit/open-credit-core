package com.lumexpay.vortexa.credit.controller;

import com.lumexpay.vortexa.credit.rules.RuleEngine;
import com.lumexpay.vortexa.credit.rules.model.ScoringRules;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for transparent access to credit scoring rules.
 * 
 * This controller exposes the OpenCredit rules so that:
 * - Merchants can understand how they are scored
 * - Lenders can audit the methodology
 * - Regulators can verify fairness
 * - Community can propose improvements
 */
@RestController
@RequestMapping("/rules")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Rules Engine", description = "Transparent access to OpenCredit scoring rules")
public class RulesController {

    private final RuleEngine ruleEngine;

    /**
     * Get current rules version and summary.
     */
    @GetMapping("/version")
    @Operation(summary = "Get rules version", description = "Returns current rules version and metadata")
    public ResponseEntity<?> getRulesVersion() {
        ScoringRules rules = ruleEngine.getRules();
        
        return ResponseEntity.ok(Map.of(
            "version", rules.getVersion(),
            "lastUpdated", rules.getLastUpdated() != null ? rules.getLastUpdated() : "unknown",
            "maintainers", rules.getMaintainers() != null ? rules.getMaintainers() : List.of(),
            "componentCount", rules.getScoring() != null && rules.getScoring().getComponents() != null ?
                rules.getScoring().getComponents().size() : 0,
            "eligibilityRuleCount", rules.getEligibility() != null && rules.getEligibility().getRules() != null ?
                rules.getEligibility().getRules().size() : 0
        ));
    }

    /**
     * Get complete rules (full transparency).
     */
    @GetMapping("/full")
    @Operation(
        summary = "Get complete rules",
        description = "Returns ALL scoring rules in full detail for complete transparency"
    )
    public ResponseEntity<ScoringRules> getFullRules() {
        return ResponseEntity.ok(ruleEngine.getRules());
    }

    /**
     * Get scoring methodology explanation.
     */
    @GetMapping("/methodology")
    @Operation(
        summary = "Get scoring methodology",
        description = "Human-readable explanation of how credit scores are calculated"
    )
    public ResponseEntity<?> getMethodology() {
        ScoringRules rules = ruleEngine.getRules();
        
        Map<String, Object> methodology = new HashMap<>();
        
        methodology.put("overview", Map.of(
            "name", rules.getMetadata() != null ? rules.getMetadata().getName() : "OpenCredit Scoring",
            "description", rules.getMetadata() != null ? rules.getMetadata().getDescription() : "",
            "scoreRange", "0-100",
            "formula", "Weighted sum of component scores"
        ));
        
        // Explain each component
        if (rules.getScoring() != null && rules.getScoring().getComponents() != null) {
            Map<String, Object> components = new HashMap<>();
            
            rules.getScoring().getComponents().forEach((name, comp) -> {
                Map<String, Object> componentInfo = new HashMap<>();
                componentInfo.put("weight", comp.getWeight() != null ? 
                    comp.getWeight().multiply(BigDecimal.valueOf(100)) + "%" : "N/A");
                componentInfo.put("description", comp.getDescription());
                componentInfo.put("metric", comp.getMetric());
                
                if (comp.getTiers() != null) {
                    componentInfo.put("tiers", comp.getTiers().stream()
                        .map(t -> Map.of(
                            "range", formatRange(t.getMin(), t.getMax(), comp.getUnit()),
                            "score", t.getScore(),
                            "label", t.getLabel(),
                            "description", t.getDescription() != null ? t.getDescription() : ""
                        ))
                        .collect(Collectors.toList()));
                }
                
                components.put(name, componentInfo);
            });
            
            methodology.put("components", components);
        }
        
        // Explain risk categories
        if (rules.getRiskCategories() != null) {
            methodology.put("riskCategories", rules.getRiskCategories().entrySet().stream()
                .map(e -> Map.of(
                    "name", e.getKey(),
                    "label", e.getValue().getLabel(),
                    "scoreRange", e.getValue().getScoreRange().getMin() + "-" + e.getValue().getScoreRange().getMax(),
                    "description", e.getValue().getDescription()
                ))
                .collect(Collectors.toList()));
        }
        
        // What we DON'T use (fairness)
        if (rules.getMetadata() != null && rules.getMetadata().getExcludedFactors() != null) {
            methodology.put("excludedFactors", Map.of(
                "description", "These factors are NEVER used in scoring to ensure fairness",
                "factors", rules.getMetadata().getExcludedFactors()
            ));
        }
        
        return ResponseEntity.ok(methodology);
    }

    /**
     * Get scoring components with weights.
     */
    @GetMapping("/components")
    @Operation(summary = "Get scoring components", description = "List all scoring components and their weights")
    public ResponseEntity<?> getComponents() {
        ScoringRules rules = ruleEngine.getRules();
        
        if (rules.getScoring() == null || rules.getScoring().getComponents() == null) {
            return ResponseEntity.ok(Map.of("components", List.of()));
        }
        
        List<Map<String, Object>> components = rules.getScoring().getComponents().entrySet().stream()
            .map(e -> {
                Map<String, Object> comp = new HashMap<>();
                comp.put("name", e.getKey());
                comp.put("weight", e.getValue().getWeight());
                comp.put("weightPercent", e.getValue().getWeight().multiply(BigDecimal.valueOf(100)).intValue() + "%");
                comp.put("description", e.getValue().getDescription());
                comp.put("metric", e.getValue().getMetric());
                return comp;
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(Map.of(
            "totalComponents", components.size(),
            "components", components
        ));
    }

    /**
     * Get eligibility requirements.
     */
    @GetMapping("/eligibility")
    @Operation(
        summary = "Get eligibility rules",
        description = "List all requirements a merchant must meet for loan eligibility"
    )
    public ResponseEntity<?> getEligibilityRules() {
        ScoringRules rules = ruleEngine.getRules();
        
        if (rules.getEligibility() == null || rules.getEligibility().getRules() == null) {
            return ResponseEntity.ok(Map.of("rules", List.of()));
        }
        
        List<Map<String,String>> eligibilityRules = rules.getEligibility().getRules().stream()
            .map(r -> Map.of(
                "id", r.getId(),
                "name", r.getName(),
                "description", r.getDescription() != null ? r.getDescription() : "",
                "requirement", formatCondition(r.getCondition()),
                "failureMessage", r.getFailureMessage() != null ? r.getFailureMessage() : "",
                "recommendation", r.getRecommendation() != null ? r.getRecommendation() : ""
            ))
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(Map.of(
            "description", rules.getEligibility().getDescription(),
            "totalRules", eligibilityRules.size(),
            "mustPassAll", true,
            "rules", eligibilityRules
        ));
    }

    /**
     * Get loan parameters by risk category.
     */
    @GetMapping("/loan-parameters")
    @Operation(summary = "Get loan parameters", description = "Loan amount, tenure, and rate rules by risk category")
    public ResponseEntity<?> getLoanParameters() {
        ScoringRules rules = ruleEngine.getRules();
        
        if (rules.getLoanParameters() == null) {
            return ResponseEntity.ok(Map.of("parameters", Map.of()));
        }
        
        ScoringRules.LoanParameters params = rules.getLoanParameters();
        
        Map<String, Object> result = new HashMap<>();
        result.put("description", params.getDescription());
        
        // Amount rules
        if (params.getAmount() != null) {
            result.put("amount", Map.of(
                "description", params.getAmount().getDescription(),
                "byRiskCategory", params.getAmount().getByRiskCategory(),
                "limits", params.getAmount().getLimits()
            ));
        }
        
        // Tenure rules
        if (params.getTenure() != null) {
            result.put("tenure", Map.of(
                "description", params.getTenure().getDescription(),
                "byRiskCategory", params.getTenure().getByRiskCategory(),
                "consistencyAdjustment", params.getTenure().getConsistencyAdjustment()
            ));
        }
        
        // Interest rate rules
        if (params.getInterestRate() != null) {
            result.put("interestRate", Map.of(
                "description", params.getInterestRate().getDescription(),
                "byRiskCategory", params.getInterestRate().getByRiskCategory(),
                "regulatoryNote", params.getInterestRate().getRegulatoryNote()
            ));
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * Get governance and contribution guidelines.
     */
    @GetMapping("/governance")
    @Operation(summary = "Get governance rules", description = "How rules are changed and community contribution process")
    public ResponseEntity<?> getGovernance() {
        ScoringRules rules = ruleEngine.getRules();
        
        if (rules.getGovernance() == null) {
            return ResponseEntity.ok(Map.of(
                "message", "Governance rules not defined"
            ));
        }
        
        return ResponseEntity.ok(Map.of(
            "description", rules.getGovernance().getDescription(),
            "changeProcess", rules.getGovernance().getChangeProcess(),
            "principles", rules.getGovernance().getPrinciples(),
            "contributeUrl", "https://github.com/opencredit/rules"
        ));
    }

    /**
     * Get changelog.
     */
    @GetMapping("/changelog")
    @Operation(summary = "Get rules changelog", description = "History of rule changes")
    public ResponseEntity<?> getChangelog() {
        ScoringRules rules = ruleEngine.getRules();
        
        return ResponseEntity.ok(Map.of(
            "currentVersion", rules.getVersion(),
            "changelog", rules.getChangelog() != null ? rules.getChangelog() : List.of()
        ));
    }

    /**
     * Simulate scoring for given metrics (for understanding/testing).
     */
    @PostMapping("/simulate")
    @Operation(
        summary = "Simulate credit score",
        description = "Calculate what score would result from given metrics (for understanding the rules)"
    )
    public ResponseEntity<?> simulateScore(@RequestBody SimulationRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        // Calculate each component score
        ScoringRules rules = ruleEngine.getRules();
        if (rules.getScoring() != null && rules.getScoring().getComponents() != null) {
            BigDecimal totalScore = BigDecimal.ZERO;
            Map<String, Object> componentResults = new HashMap<>();
            
            for (Map.Entry<String, ScoringRules.ScoringComponent> entry : 
                    rules.getScoring().getComponents().entrySet()) {
                
                String name = entry.getKey();
                ScoringRules.ScoringComponent comp = entry.getValue();
                BigDecimal metricValue = getSimulationMetric(name, request);
                
                BigDecimal score = calculateTierScore(metricValue, comp);
                BigDecimal weighted = score.multiply(comp.getWeight());
                totalScore = totalScore.add(weighted);
                
                componentResults.put(name, Map.of(
                    "inputValue", metricValue,
                    "rawScore", score,
                    "weight", comp.getWeight(),
                    "weightedScore", weighted
                ));
            }
            
            result.put("componentScores", componentResults);
            result.put("totalScore", totalScore.intValue());
            result.put("riskCategory", totalScore.intValue() >= 80 ? "LOW" : 
                                       totalScore.intValue() >= 60 ? "MEDIUM" : "HIGH");
        }
        
        return ResponseEntity.ok(result);
    }

    // Helper methods

    private String formatRange(BigDecimal min, BigDecimal max, String unit) {
        String unitSuffix = unit != null && unit.equals("INR") ? " ₹" : "";
        if (min == null) {
            return "Below " + formatNumber(max) + unitSuffix;
        }
        if (max == null) {
            return formatNumber(min) + unitSuffix + "+";
        }
        return formatNumber(min) + " - " + formatNumber(max) + unitSuffix;
    }

    private String formatNumber(BigDecimal num) {
        if (num == null) return "N/A";
        double val = num.doubleValue();
        if (val >= 100000) return String.format("%.1fL", val / 100000);
        if (val >= 1000) return String.format("%.0fK", val / 1000);
        return String.format("%.0f", val);
    }

    private String formatCondition(ScoringRules.RuleCondition cond) {
        if (cond == null) return "N/A";
        String unit = cond.getUnit() != null ? " " + cond.getUnit() : "";
        return cond.getMetric() + " " + cond.getOperator() + " " + cond.getValue() + unit;
    }

    private BigDecimal getSimulationMetric(String componentName, SimulationRequest request) {
        return switch (componentName) {
            case "volume" -> request.getMonthlyVolume() != null ? 
                request.getMonthlyVolume() : BigDecimal.ZERO;
            case "consistency" -> request.getConsistencyScore() != null ?
                request.getConsistencyScore() : BigDecimal.valueOf(70);
            case "growth" -> request.getGrowthRate() != null ?
                request.getGrowthRate() : BigDecimal.ZERO;
            case "bounce_rate" -> request.getBounceRate() != null ?
                request.getBounceRate() : BigDecimal.valueOf(5);
            case "concentration" -> request.getCustomerConcentration() != null ?
                request.getCustomerConcentration() : BigDecimal.valueOf(30);
            default -> BigDecimal.ZERO;
        };
    }

    private BigDecimal calculateTierScore(BigDecimal value, ScoringRules.ScoringComponent comp) {
        if (comp.getTiers() == null) return BigDecimal.valueOf(50);
        
        for (ScoringRules.ScoringTier tier : comp.getTiers()) {
            boolean matchesMin = tier.getMin() == null || value.compareTo(tier.getMin()) >= 0;
            boolean matchesMax = tier.getMax() == null || value.compareTo(tier.getMax()) < 0;
            if (matchesMin && matchesMax) {
                return tier.getScore();
            }
        }
        return BigDecimal.valueOf(20);
    }

    // Request DTOs
    
    @lombok.Data
    public static class SimulationRequest {
        private BigDecimal monthlyVolume;
        private BigDecimal consistencyScore;
        private BigDecimal growthRate;
        private BigDecimal bounceRate;
        private BigDecimal customerConcentration;
    }
}
