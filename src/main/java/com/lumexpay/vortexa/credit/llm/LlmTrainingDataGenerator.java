package com.lumexpay.vortexa.credit.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumexpay.vortexa.credit.dto.FinancialMetrics;
import com.lumexpay.vortexa.credit.rules.RuleEngine;
import com.lumexpay.vortexa.credit.rules.RuleEngine.*;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Generates training data for fine-tuning LLMs on credit scoring rules.
 * 
 * This creates instruction-response pairs that teach the LLM:
 * 1. How to explain credit scores
 * 2. How to provide recommendations
 * 3. How to answer credit-related questions
 * 
 * The LLM learns FROM the rules, not to MAKE decisions.
 * Decisions are still made by the deterministic rule engine.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LlmTrainingDataGenerator {

    private final RuleEngine ruleEngine;
    private final ObjectMapper objectMapper;
    private final Random random = new Random(42);

    /**
     * Generate training dataset in JSONL format for fine-tuning.
     * 
     * Supports formats:
     * - OpenAI fine-tuning format
     * - Hugging Face format
     * - Alpaca format
     */
    public void generateTrainingData(String outputPath, int sampleCount) throws IOException {
        log.info("Generating {} training samples for LLM fine-tuning", sampleCount);

        List<TrainingExample> examples = new ArrayList<>();

        // Generate diverse merchant profiles
        for (int i = 0; i < sampleCount; i++) {
            FinancialMetrics metrics = generateRandomMetrics();
            ScoringResult result = ruleEngine.calculateScore(metrics);
            EligibilityResult eligibility = ruleEngine.checkEligibility(metrics, 0);
            LoanParametersResult loanParams = ruleEngine.calculateLoanParameters(
                result.getRiskCategory(),
                metrics.getAverageMonthlyVolume(),
                metrics.getConsistencyScore()
            );

            // Generate multiple training examples from each profile
            examples.addAll(generateExamplesFromProfile(metrics, result, eligibility, loanParams));
        }

        // Shuffle examples
        Collections.shuffle(examples, random);

        // Write in different formats
        writeOpenAIFormat(examples, outputPath + "/openai_training.jsonl");
        writeAlpacaFormat(examples, outputPath + "/alpaca_training.json");
        writeHuggingFaceFormat(examples, outputPath + "/hf_training.jsonl");

        log.info("Generated {} training examples", examples.size());
    }

    /**
     * Generate training examples from a single merchant profile.
     */
    private List<TrainingExample> generateExamplesFromProfile(
            FinancialMetrics metrics,
            ScoringResult result,
            EligibilityResult eligibility,
            LoanParametersResult loanParams) {

        List<TrainingExample> examples = new ArrayList<>();
        String context = buildContext(metrics, result);

        // Example 1: Explain credit score
        examples.add(TrainingExample.builder()
            .instruction("Explain this merchant's credit score in simple terms.")
            .input(context)
            .output(generateScoreExplanation(metrics, result))
            .category("score_explanation")
            .build());

        // Example 2: Why this score?
        examples.add(TrainingExample.builder()
            .instruction("Why did this merchant get a score of " + result.getCreditScore() + "?")
            .input(context)
            .output(generateWhyThisScore(metrics, result))
            .category("score_reasoning")
            .build());

        // Example 3: Eligibility explanation
        examples.add(TrainingExample.builder()
            .instruction("Is this merchant eligible for a loan? Explain why or why not.")
            .input(context)
            .output(generateEligibilityExplanation(eligibility, loanParams))
            .category("eligibility")
            .build());

        // Example 4: Improvement recommendations
        examples.add(TrainingExample.builder()
            .instruction("What can this merchant do to improve their credit score?")
            .input(context)
            .output(generateRecommendations(metrics, result))
            .category("recommendations")
            .build());

        // Example 5: Risk analysis
        examples.add(TrainingExample.builder()
            .instruction("Provide a risk analysis for lending to this merchant.")
            .input(context)
            .output(generateRiskAnalysis(metrics, result, loanParams))
            .category("risk_analysis")
            .build());

        // Example 6: Component breakdown
        examples.add(TrainingExample.builder()
            .instruction("Break down the credit score components for this merchant.")
            .input(context)
            .output(generateComponentBreakdown(result))
            .category("component_breakdown")
            .build());

        // Example 7: Specific metric questions
        if (metrics.getBounceRate().compareTo(new BigDecimal("10")) > 0) {
            examples.add(TrainingExample.builder()
                .instruction("This merchant has a high bounce rate. What does this mean?")
                .input(context)
                .output(generateBounceRateExplanation(metrics))
                .category("metric_explanation")
                .build());
        }

        // Example 8: Comparison to thresholds
        examples.add(TrainingExample.builder()
            .instruction("How does this merchant compare to the eligibility thresholds?")
            .input(context)
            .output(generateThresholdComparison(metrics))
            .category("threshold_comparison")
            .build());

        return examples;
    }

    /**
     * Build context string from metrics and result.
     */
    private String buildContext(FinancialMetrics metrics, ScoringResult result) {
        return String.format("""
            Merchant Profile:
            - Credit Score: %d/100
            - Risk Category: %s
            - Average Monthly Volume: ₹%s
            - Transaction Count: %d
            - Unique Customers: %d
            - Bounce Rate: %.1f%%
            - Customer Concentration: %.1f%%
            - Growth Rate: %.1f%%
            - Consistency Score: %.1f/100
            """,
            result.getCreditScore(),
            result.getRiskCategory(),
            formatCurrency(metrics.getAverageMonthlyVolume()),
            metrics.getTotalTransactionCount(),
            metrics.getUniqueCustomerCount(),
            metrics.getBounceRate().doubleValue(),
            metrics.getCustomerConcentration().doubleValue(),
            metrics.getGrowthRate().doubleValue(),
            metrics.getConsistencyScore().doubleValue()
        );
    }

    /**
     * Generate score explanation.
     */
    private String generateScoreExplanation(FinancialMetrics metrics, ScoringResult result) {
        int score = result.getCreditScore();
        String category = result.getRiskCategory();
        
        StringBuilder sb = new StringBuilder();
        
        // Opening based on score range
        if (score >= 80) {
            sb.append("This merchant has an excellent credit score of ")
              .append(score).append(" out of 100, placing them in the LOW risk category. ");
        } else if (score >= 60) {
            sb.append("This merchant has a good credit score of ")
              .append(score).append(" out of 100, placing them in the MEDIUM risk category. ");
        } else {
            sb.append("This merchant has a credit score of ")
              .append(score).append(" out of 100, placing them in the HIGH risk category. ");
        }

        // Explain key factors
        sb.append("\n\nKey factors:\n");
        
        // Volume
        BigDecimal volume = metrics.getAverageMonthlyVolume();
        if (volume.compareTo(new BigDecimal("200000")) >= 0) {
            sb.append("✓ Strong transaction volume of ₹").append(formatCurrency(volume))
              .append(" monthly shows healthy business activity.\n");
        } else if (volume.compareTo(new BigDecimal("50000")) >= 0) {
            sb.append("• Moderate transaction volume of ₹").append(formatCurrency(volume))
              .append(" monthly.\n");
        } else {
            sb.append("⚠ Low transaction volume of ₹").append(formatCurrency(volume))
              .append(" monthly limits loan capacity.\n");
        }

        // Bounce rate
        if (metrics.getBounceRate().compareTo(new BigDecimal("5")) <= 0) {
            sb.append("✓ Excellent payment success rate with only ")
              .append(metrics.getBounceRate()).append("% failed transactions.\n");
        } else if (metrics.getBounceRate().compareTo(new BigDecimal("15")) <= 0) {
            sb.append("• Bounce rate of ").append(metrics.getBounceRate())
              .append("% is acceptable but has room for improvement.\n");
        } else {
            sb.append("⚠ High bounce rate of ").append(metrics.getBounceRate())
              .append("% is a concern for lenders.\n");
        }

        // Concentration
        if (metrics.getCustomerConcentration().compareTo(new BigDecimal("30")) <= 0) {
            sb.append("✓ Well-diversified customer base reduces business risk.\n");
        } else {
            sb.append("⚠ High customer concentration (")
              .append(metrics.getCustomerConcentration())
              .append("%) means dependence on few customers.\n");
        }

        return sb.toString();
    }

    /**
     * Generate "why this score" explanation.
     */
    private String generateWhyThisScore(FinancialMetrics metrics, ScoringResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("The score of ").append(result.getCreditScore())
          .append(" is calculated using a weighted formula:\n\n");

        sb.append("Score = (Volume × 30%) + (Consistency × 25%) + (Growth × 15%) + (Bounce Rate × 15%) + (Concentration × 15%)\n\n");

        if (result.getComponentScores() != null) {
            sb.append("Your component scores:\n");
            result.getComponentScores().forEach((name, comp) -> {
                sb.append(String.format("- %s: %.0f/100 (contributes %.0f points)\n",
                    formatComponentName(name),
                    comp.getScore().doubleValue(),
                    comp.getScore().multiply(comp.getWeight()).doubleValue()));
            });
        }

        // Add warnings
        if (!result.getWarnings().isEmpty()) {
            sb.append("\nAreas affecting your score:\n");
            result.getWarnings().forEach(w -> sb.append("⚠ ").append(w).append("\n"));
        }

        // Add strengths
        if (!result.getStrengths().isEmpty()) {
            sb.append("\nStrengths boosting your score:\n");
            result.getStrengths().forEach(s -> sb.append("✓ ").append(s).append("\n"));
        }

        return sb.toString();
    }

    /**
     * Generate eligibility explanation.
     */
    private String generateEligibilityExplanation(EligibilityResult eligibility, LoanParametersResult loanParams) {
        if (eligibility.isEligible()) {
            return String.format("""
                Yes, this merchant is eligible for a loan!
                
                Loan Terms Available:
                • Maximum Loan Amount: ₹%s
                • Maximum Tenure: %d days
                • Interest Rate: %.1f%% per annum
                
                All eligibility criteria are met:
                ✓ Monthly volume above ₹25,000 minimum
                ✓ At least 20 transactions recorded
                ✓ Bounce rate below 20%% threshold
                ✓ Business operating for 3+ months
                ✓ No fraud indicators detected
                """,
                formatCurrency(loanParams.getEligibleAmount()),
                loanParams.getMaxTenureDays(),
                loanParams.getInterestRateAnnual().doubleValue()
            );
        } else {
            return String.format("""
                This merchant is currently NOT eligible for a loan.
                
                Reason: %s
                
                %s
                
                To become eligible, the merchant should address the above issue and maintain good transaction patterns for at least 30 days before reapplying.
                """,
                eligibility.getFailureReason(),
                eligibility.getRecommendation() != null ? "Recommendation: " + eligibility.getRecommendation() : ""
            );
        }
    }

    /**
     * Generate recommendations.
     */
    private String generateRecommendations(FinancialMetrics metrics, ScoringResult result) {
        List<String> recommendations = new ArrayList<>();
        
        // Check each component and add relevant recommendations
        if (metrics.getBounceRate().compareTo(new BigDecimal("5")) > 0) {
            recommendations.add("Reduce transaction failures: Ensure customers have sufficient funds before initiating payments. Consider sending payment reminders.");
        }
        
        if (metrics.getCustomerConcentration().compareTo(new BigDecimal("30")) > 0) {
            recommendations.add("Diversify customer base: Acquire new customers through marketing. Don't rely too heavily on a few large customers.");
        }
        
        if (metrics.getGrowthRate().compareTo(BigDecimal.ZERO) < 0) {
            recommendations.add("Reverse declining trend: Focus on customer retention and increasing transaction volume. Consider promotional offers.");
        }
        
        if (metrics.getAverageMonthlyVolume().compareTo(new BigDecimal("100000")) < 0) {
            recommendations.add("Increase transaction volume: Encourage more customers to pay digitally. Accept UPI for all transactions.");
        }
        
        if (metrics.getConsistencyScore().compareTo(new BigDecimal("60")) < 0) {
            recommendations.add("Improve consistency: Maintain steady monthly volumes. Avoid extreme fluctuations in business activity.");
        }

        if (recommendations.isEmpty()) {
            return "Congratulations! Your credit profile is excellent. Continue maintaining your current business practices to preserve your strong credit standing.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Here are specific actions to improve your credit score:\n\n");
        for (int i = 0; i < recommendations.size(); i++) {
            sb.append(i + 1).append(". ").append(recommendations.get(i)).append("\n\n");
        }
        
        sb.append("Focus on these areas for the next 30-60 days, then request a re-assessment to see your improved score.");
        
        return sb.toString();
    }

    /**
     * Generate risk analysis.
     */
    private String generateRiskAnalysis(FinancialMetrics metrics, ScoringResult result, LoanParametersResult loanParams) {
        String riskLevel = result.getRiskCategory();
        
        StringBuilder sb = new StringBuilder();
        sb.append("RISK ANALYSIS FOR LENDING DECISION\n");
        sb.append("===================================\n\n");
        
        sb.append("Risk Category: ").append(riskLevel).append("\n");
        sb.append("Credit Score: ").append(result.getCreditScore()).append("/100\n\n");
        
        sb.append("KEY RISK FACTORS:\n");
        
        // Analyze each risk dimension
        if (metrics.getBounceRate().compareTo(new BigDecimal("10")) > 0) {
            sb.append("⚠ HIGH - Bounce Rate: ").append(metrics.getBounceRate())
              .append("% indicates payment collection risk\n");
        } else if (metrics.getBounceRate().compareTo(new BigDecimal("5")) > 0) {
            sb.append("• MEDIUM - Bounce Rate: ").append(metrics.getBounceRate())
              .append("% is acceptable\n");
        } else {
            sb.append("✓ LOW - Bounce Rate: ").append(metrics.getBounceRate())
              .append("% shows reliable payments\n");
        }

        if (metrics.getCustomerConcentration().compareTo(new BigDecimal("50")) > 0) {
            sb.append("⚠ HIGH - Customer Concentration: ").append(metrics.getCustomerConcentration())
              .append("% creates dependency risk\n");
        } else {
            sb.append("✓ LOW - Customer Concentration: ").append(metrics.getCustomerConcentration())
              .append("% shows diversification\n");
        }

        sb.append("\nRECOMMENDED LOAN STRUCTURE:\n");
        sb.append("• Amount: ₹").append(formatCurrency(loanParams.getEligibleAmount())).append("\n");
        sb.append("• Tenure: ").append(loanParams.getMaxTenureDays()).append(" days\n");
        sb.append("• Rate: ").append(loanParams.getInterestRateAnnual()).append("% p.a.\n");
        
        sb.append("\nLENDING RECOMMENDATION: ");
        if ("LOW".equals(riskLevel)) {
            sb.append("APPROVE - Low risk, favorable terms recommended.");
        } else if ("MEDIUM".equals(riskLevel)) {
            sb.append("APPROVE WITH CAUTION - Standard terms, monitor performance.");
        } else {
            sb.append("REVIEW REQUIRED - Higher risk, consider additional verification.");
        }

        return sb.toString();
    }

    /**
     * Generate component breakdown.
     */
    private String generateComponentBreakdown(ScoringResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREDIT SCORE BREAKDOWN\n");
        sb.append("======================\n\n");
        sb.append("Your score of ").append(result.getCreditScore())
          .append(" is composed of these elements:\n\n");

        if (result.getComponentScores() != null) {
            result.getComponentScores().forEach((name, comp) -> {
                sb.append(String.format("%-15s: %3.0f/100 × %2.0f%% = %5.1f points",
                    formatComponentName(name),
                    comp.getScore().doubleValue(),
                    comp.getWeight().multiply(new BigDecimal("100")).doubleValue(),
                    comp.getScore().multiply(comp.getWeight()).doubleValue()));
                
                if (comp.getLabel() != null) {
                    sb.append(" (").append(comp.getLabel()).append(")");
                }
                sb.append("\n");
            });
        }

        sb.append("\n─────────────────────────────────\n");
        sb.append(String.format("TOTAL SCORE: %d/100\n", result.getCreditScore()));

        return sb.toString();
    }

    /**
     * Generate bounce rate explanation.
     */
    private String generateBounceRateExplanation(FinancialMetrics metrics) {
        return String.format("""
            Your bounce rate of %.1f%% means that %.1f%% of your transactions failed.
            
            This could happen due to:
            • Customers having insufficient funds
            • Technical issues with payment processing
            • Incorrect account details
            • Bank server issues
            
            A high bounce rate (above 10%%) is concerning because:
            1. It suggests customers may have payment difficulties
            2. It indicates potential collection risk for loans
            3. It reduces your credit score significantly
            
            To improve:
            • Verify customer payment capacity before large transactions
            • Send payment reminders before due dates
            • Offer multiple payment options
            • Follow up on failed payments promptly
            
            Target bounce rate: Below 5%% for excellent credit standing.
            """,
            metrics.getBounceRate().doubleValue(),
            metrics.getBounceRate().doubleValue()
        );
    }

    /**
     * Generate threshold comparison.
     */
    private String generateThresholdComparison(FinancialMetrics metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("ELIGIBILITY THRESHOLD COMPARISON\n");
        sb.append("================================\n\n");

        // Volume
        sb.append("Monthly Volume:\n");
        sb.append("  Your value: ₹").append(formatCurrency(metrics.getAverageMonthlyVolume())).append("\n");
        sb.append("  Minimum required: ₹25,000\n");
        sb.append("  Status: ").append(
            metrics.getAverageMonthlyVolume().compareTo(new BigDecimal("25000")) >= 0 ? 
            "✓ PASS" : "✗ FAIL").append("\n\n");

        // Transaction count
        sb.append("Transaction Count:\n");
        sb.append("  Your value: ").append(metrics.getTotalTransactionCount()).append("\n");
        sb.append("  Minimum required: 20\n");
        sb.append("  Status: ").append(
            metrics.getTotalTransactionCount() >= 20 ? "✓ PASS" : "✗ FAIL").append("\n\n");

        // Bounce rate
        sb.append("Bounce Rate:\n");
        sb.append("  Your value: ").append(metrics.getBounceRate()).append("%\n");
        sb.append("  Maximum allowed: 20%\n");
        sb.append("  Status: ").append(
            metrics.getBounceRate().compareTo(new BigDecimal("20")) <= 0 ? 
            "✓ PASS" : "✗ FAIL").append("\n");

        return sb.toString();
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private FinancialMetrics generateRandomMetrics() {
        // Generate realistic random metrics covering all scenarios
        BigDecimal volume = randomBigDecimal(10000, 1000000);
        BigDecimal bounceRate = randomBigDecimal(0, 25);
        BigDecimal concentration = randomBigDecimal(10, 80);
        BigDecimal growthRate = randomBigDecimal(-30, 50);
        BigDecimal consistency = randomBigDecimal(20, 95);
        int transactions = random.nextInt(500) + 10;
        int customers = random.nextInt(100) + 3;

        return FinancialMetrics.builder()
            .averageMonthlyVolume(volume)
            .last3MonthsVolume(volume.multiply(new BigDecimal("3")))
            .last6MonthsVolume(volume.multiply(new BigDecimal("6")))
            .totalTransactionCount(transactions)
            .successfulTransactionCount((int)(transactions * (1 - bounceRate.doubleValue()/100)))
            .failedTransactionCount((int)(transactions * bounceRate.doubleValue()/100))
            .uniqueCustomerCount(customers)
            .bounceRate(bounceRate)
            .customerConcentration(concentration)
            .growthRate(growthRate)
            .consistencyScore(consistency)
            .coefficientOfVariation(new BigDecimal("1").subtract(consistency.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)))
            .averageTransactionValue(volume.divide(new BigDecimal(transactions / 30), 2, RoundingMode.HALF_UP))
            .build();
    }

    private BigDecimal randomBigDecimal(double min, double max) {
        double value = min + (max - min) * random.nextDouble();
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0";
        double val = amount.doubleValue();
        if (val >= 100000) return String.format("%.2f L", val / 100000);
        if (val >= 1000) return String.format("%.0f K", val / 1000);
        return String.format("%.0f", val);
    }

    private String formatComponentName(String name) {
        return switch (name) {
            case "volume" -> "Volume";
            case "consistency" -> "Consistency";
            case "growth" -> "Growth";
            case "bounce_rate" -> "Bounce Rate";
            case "concentration" -> "Concentration";
            default -> name;
        };
    }

    // ========================================================================
    // Output Format Writers
    // ========================================================================

    private void writeOpenAIFormat(List<TrainingExample> examples, String path) throws IOException {
        try (FileWriter writer = new FileWriter(path)) {
            for (TrainingExample ex : examples) {
                Map<String, Object> entry = Map.of(
                    "messages", List.of(
                        Map.of("role", "system", "content", 
                            "You are a credit advisor AI that explains credit scores and provides recommendations based on OpenCredit scoring rules."),
                        Map.of("role", "user", "content", ex.getInstruction() + "\n\n" + ex.getInput()),
                        Map.of("role", "assistant", "content", ex.getOutput())
                    )
                );
                writer.write(objectMapper.writeValueAsString(entry) + "\n");
            }
        }
        log.info("Wrote OpenAI format to {}", path);
    }

    private void writeAlpacaFormat(List<TrainingExample> examples, String path) throws IOException {
        try (FileWriter writer = new FileWriter(path)) {
            List<Map<String, String>> data = examples.stream()
                .map(ex -> Map.of(
                    "instruction", ex.getInstruction(),
                    "input", ex.getInput(),
                    "output", ex.getOutput()
                ))
                .toList();
            writer.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data));
        }
        log.info("Wrote Alpaca format to {}", path);
    }

    private void writeHuggingFaceFormat(List<TrainingExample> examples, String path) throws IOException {
        try (FileWriter writer = new FileWriter(path)) {
            for (TrainingExample ex : examples) {
                Map<String, String> entry = Map.of(
                    "text", String.format("<s>[INST] %s\n\n%s [/INST] %s </s>",
                        ex.getInstruction(), ex.getInput(), ex.getOutput())
                );
                writer.write(objectMapper.writeValueAsString(entry) + "\n");
            }
        }
        log.info("Wrote HuggingFace format to {}", path);
    }

    // ========================================================================
    // Data Classes
    // ========================================================================

    @Data
    @Builder
    public static class TrainingExample {
        private String instruction;
        private String input;
        private String output;
        private String category;
    }
}
