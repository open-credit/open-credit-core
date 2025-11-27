package com.lumexpay.vortexa.credit.llm.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.lumexpay.vortexa.credit.dto.FinancialMetrics;
import com.lumexpay.vortexa.credit.rules.RuleEngine;
import com.lumexpay.vortexa.credit.rules.RuleEngine.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Complete Training Pipeline for Fine-tuning LLMs on Credit Scoring Rules.
 * 
 * This generates training data that teaches the LLM to:
 * 1. EXPLAIN scores (not calculate them)
 * 2. DESCRIBE rules (not execute them)
 * 3. RECOMMEND improvements (based on rules)
 * 4. ANSWER questions (about the scoring system)
 * 
 * IMPORTANT: The LLM learns to EXPLAIN decisions made by the Rule Engine,
 * not to MAKE decisions itself.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CreditScoringTrainingPipeline {

    private final RuleEngine ruleEngine;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Random random = new Random(42); // Reproducible randomness

    // ========================================================================
    // MAIN TRAINING DATA GENERATION
    // ========================================================================

    /**
     * Generate complete training dataset for LLM fine-tuning.
     * 
     * @param outputDir Directory to write training files
     * @param numProfiles Number of merchant profiles to generate
     */
    public TrainingStats generateCompleteTrainingData(String outputDir, int numProfiles) throws IOException {
        log.info("Starting training data generation: {} profiles", numProfiles);
        
        Path outputPath = Paths.get(outputDir);
        Files.createDirectories(outputPath);

        List<TrainingExample> allExamples = new ArrayList<>();
        
        // 1. Generate examples from synthetic merchant profiles
        log.info("Generating profile-based examples...");
        allExamples.addAll(generateProfileBasedExamples(numProfiles));
        
        // 2. Generate rule explanation examples
        log.info("Generating rule explanation examples...");
        allExamples.addAll(generateRuleExplanationExamples());
        
        // 3. Generate FAQ examples
        log.info("Generating FAQ examples...");
        allExamples.addAll(generateFAQExamples());
        
        // 4. Generate edge case examples
        log.info("Generating edge case examples...");
        allExamples.addAll(generateEdgeCaseExamples());
        
        // 5. Generate conversation examples
        log.info("Generating conversation examples...");
        allExamples.addAll(generateConversationExamples());

        // Shuffle and split
        Collections.shuffle(allExamples, random);
        
        int trainSize = (int) (allExamples.size() * 0.8);
        int valSize = (int) (allExamples.size() * 0.1);
        
        List<TrainingExample> trainSet = allExamples.subList(0, trainSize);
        List<TrainingExample> valSet = allExamples.subList(trainSize, trainSize + valSize);
        List<TrainingExample> testSet = allExamples.subList(trainSize + valSize, allExamples.size());

        // Write in multiple formats
        writeOpenAIFormat(trainSet, outputPath.resolve("train_openai.jsonl"));
        writeOpenAIFormat(valSet, outputPath.resolve("val_openai.jsonl"));
        writeOpenAIFormat(testSet, outputPath.resolve("test_openai.jsonl"));
        
        writeAlpacaFormat(trainSet, outputPath.resolve("train_alpaca.json"));
        writeLlamaFormat(trainSet, outputPath.resolve("train_llama.jsonl"));
        writeHuggingFaceFormat(trainSet, outputPath.resolve("train_hf.jsonl"));
        
        // Write statistics
        TrainingStats stats = TrainingStats.builder()
            .totalExamples(allExamples.size())
            .trainExamples(trainSet.size())
            .valExamples(valSet.size())
            .testExamples(testSet.size())
            .categoryCounts(countCategories(allExamples))
            .build();
        
        jsonMapper.writerWithDefaultPrettyPrinter()
            .writeValue(outputPath.resolve("stats.json").toFile(), stats);
        
        log.info("Training data generation complete: {}", stats);
        return stats;
    }

    // ========================================================================
    // EXAMPLE GENERATORS
    // ========================================================================

    /**
     * Generate examples from synthetic merchant profiles.
     */
    private List<TrainingExample> generateProfileBasedExamples(int numProfiles) {
        List<TrainingExample> examples = new ArrayList<>();
        
        for (int i = 0; i < numProfiles; i++) {
            FinancialMetrics metrics = generateRealisticMetrics();
            ScoringResult result = ruleEngine.calculateScore(metrics);
            EligibilityResult eligibility = ruleEngine.checkEligibility(metrics, 0);
            LoanParametersResult loanParams = ruleEngine.calculateLoanParameters(
                result.getRiskCategory(),
                metrics.getAverageMonthlyVolume(),
                metrics.getConsistencyScore()
            );

            String context = buildMerchantContext(metrics, result);

            // Score explanation
            examples.add(createExample(
                "explain_score",
                "Explain this merchant's credit score in simple terms that a small business owner would understand.",
                context,
                generateScoreExplanation(metrics, result)
            ));

            // Why this score
            examples.add(createExample(
                "score_reasoning",
                "Why did this merchant receive a score of " + result.getCreditScore() + "?",
                context,
                generateScoreReasoning(metrics, result)
            ));

            // Eligibility
            examples.add(createExample(
                "eligibility",
                "Is this merchant eligible for a loan? Explain the decision.",
                context,
                generateEligibilityResponse(eligibility, loanParams)
            ));

            // Recommendations
            examples.add(createExample(
                "recommendations",
                "What specific actions can this merchant take to improve their credit score?",
                context,
                generateRecommendations(metrics, result)
            ));

            // Risk analysis
            examples.add(createExample(
                "risk_analysis",
                "Provide a risk analysis for a lender considering this merchant.",
                context,
                generateRiskAnalysis(metrics, result, loanParams)
            ));

            // Component breakdown
            examples.add(createExample(
                "breakdown",
                "Break down how each component contributes to this score.",
                context,
                generateBreakdown(result)
            ));

            // Specific metric questions based on profile
            if (metrics.getBounceRate().compareTo(new BigDecimal("10")) > 0) {
                examples.add(createExample(
                    "metric_bounce",
                    "This merchant has a bounce rate of " + metrics.getBounceRate() + "%. Is this concerning?",
                    context,
                    generateBounceAnalysis(metrics)
                ));
            }

            if (metrics.getCustomerConcentration().compareTo(new BigDecimal("40")) > 0) {
                examples.add(createExample(
                    "metric_concentration",
                    "The top 10 customers represent " + metrics.getCustomerConcentration() + "% of revenue. What does this mean?",
                    context,
                    generateConcentrationAnalysis(metrics)
                ));
            }

            if (metrics.getGrowthRate().compareTo(BigDecimal.ZERO) < 0) {
                examples.add(createExample(
                    "metric_growth",
                    "This business has a negative growth rate. How does this affect their credit?",
                    context,
                    generateGrowthAnalysis(metrics)
                ));
            }
        }

        return examples;
    }

    /**
     * Generate examples that explain the rules themselves.
     */
    private List<TrainingExample> generateRuleExplanationExamples() {
        List<TrainingExample> examples = new ArrayList<>();
        
        // How scoring works
        examples.add(createExample(
            "system_scoring",
            "How does the OpenCredit scoring system calculate credit scores?",
            "",
            """
            The OpenCredit scoring system evaluates merchants based on 5 key components of their UPI transaction history:

            1. **Transaction Volume (30% weight)**
               Measures monthly transaction value. Higher volume indicates stronger business activity.
               - ₹5 lakh+ monthly = 100 points (Excellent)
               - ₹2-5 lakh = 80 points (Good)
               - ₹1-2 lakh = 60 points (Fair)
               - Below ₹25,000 = 20 points (Low)

            2. **Consistency (25% weight)**
               Measures how steady the business is month-over-month. Calculated using coefficient of variation.
               - Very consistent (CV < 0.15) = 100 points
               - Consistent (CV 0.15-0.30) = 75 points
               - Variable (CV > 0.50) = 25 points

            3. **Growth Rate (15% weight)**
               Compares recent 3 months vs previous 3 months.
               - Growing 30%+ = 100 points
               - Growing 10-30% = 80 points
               - Declining more than 25% = 20 points

            4. **Bounce Rate (15% weight)**
               Percentage of failed transactions. Lower is better.
               - 0-3% failures = 100 points (Excellent)
               - 3-7% failures = 75 points (Good)
               - Above 20% = 0 points (Disqualifying)

            5. **Customer Concentration (15% weight)**
               How dependent the business is on top customers.
               - Top 10 customers < 20% of revenue = 100 points
               - Top 10 customers > 70% of revenue = 20 points

            Final score = Σ(component_score × weight), ranging from 0-100.
            """
        ));

        // Eligibility rules
        examples.add(createExample(
            "system_eligibility",
            "What are the eligibility requirements for a loan?",
            "",
            """
            To be eligible for a loan, merchants must meet ALL of these criteria:

            1. **Minimum Monthly Volume: ₹25,000**
               Your average monthly UPI transactions must be at least ₹25,000.
               Why: Ensures sufficient business activity to repay the loan.

            2. **Minimum Transaction Count: 20 transactions**
               You need at least 20 transactions in your history.
               Why: Provides enough data for accurate assessment.

            3. **Maximum Bounce Rate: 20%**
               No more than 20% of your transactions can fail.
               Why: High failure rates indicate payment collection risk.

            4. **Minimum Business Tenure: 3 months**
               Your UPI transaction history must span at least 3 months.
               Why: Shows business stability over time.

            5. **No Fraud Indicators**
               No suspicious patterns detected in your transactions.
               Why: Protects against fraud and ensures fair lending.

            If you don't meet any of these criteria, you'll receive specific guidance on what to improve.
            """
        ));

        // Risk categories
        examples.add(createExample(
            "system_risk",
            "What do the risk categories (LOW, MEDIUM, HIGH) mean?",
            "",
            """
            The risk category indicates the lending risk level and determines loan terms:

            **LOW Risk (Score 80-100)**
            - Best loan terms available
            - Loan amount: Up to 30% of monthly volume
            - Maximum tenure: 365 days
            - Interest rate: Starting at 18% per annum
            - Fast approval process
            - Higher credit limits available

            **MEDIUM Risk (Score 60-79)**
            - Standard loan terms
            - Loan amount: Up to 25% of monthly volume
            - Maximum tenure: 180 days
            - Interest rate: Around 24% per annum
            - Standard approval process
            - Moderate credit limits

            **HIGH Risk (Score 0-59)**
            - Conservative loan terms
            - Loan amount: Up to 15% of monthly volume
            - Maximum tenure: 90 days
            - Interest rate: Around 30% per annum
            - Additional verification may be required
            - Lower credit limits

            These categories help lenders make fair decisions while managing risk appropriately.
            """
        ));

        // What factors are NOT used
        examples.add(createExample(
            "system_fairness",
            "What factors are NOT used in calculating credit scores?",
            "",
            """
            OpenCredit is designed for fairness. We NEVER use these factors:

            **Personal Characteristics (Never Used)**
            - Gender or sex
            - Religion or religious practices
            - Caste or community
            - Age (beyond legal requirements)
            - Marital status
            - Family background

            **Socioeconomic Factors (Never Used)**
            - Educational qualification
            - Residential area or pin code
            - Type of housing
            - Social media activity
            - Political affiliation

            **We ONLY Use:**
            - Transaction volume (how much business you do)
            - Transaction consistency (how steady your business is)
            - Growth rate (whether you're growing)
            - Bounce rate (payment success rate)
            - Customer concentration (diversification)

            This ensures that anyone with good business fundamentals can build credit, regardless of their background. Our rules are completely transparent and open-source.
            """
        ));

        // How to improve
        examples.add(createExample(
            "system_improve",
            "What's the best way to improve my credit score?",
            "",
            """
            Here are the most effective ways to improve your OpenCredit score, in order of impact:

            **High Impact (Affects 30% of score)**
            1. **Increase Transaction Volume**
               - Encourage more customers to pay via UPI
               - Accept UPI for all transactions, even small ones
               - Target: ₹2 lakh+ monthly for good score

            **Medium-High Impact (25% of score)**
            2. **Maintain Consistency**
               - Keep monthly volumes steady
               - Avoid dramatic fluctuations
               - Plan for seasonal variations

            **Medium Impact (15% each)**
            3. **Reduce Bounce Rate**
               - Verify customer funds before large transactions
               - Send payment reminders
               - Follow up on failed payments
               - Target: Below 5% for excellent score

            4. **Show Growth**
               - Focus on acquiring new customers
               - Increase average transaction value
               - Even 10% growth helps significantly

            5. **Diversify Customers**
               - Don't depend on just a few customers
               - Target: No customer > 20% of your revenue
               - Acquire many small customers vs few large ones

            **Timeline**: Most improvements take 30-60 days to reflect in your score.
            """
        ));

        return examples;
    }

    /**
     * Generate FAQ examples.
     */
    private List<TrainingExample> generateFAQExamples() {
        List<TrainingExample> examples = new ArrayList<>();

        // Common questions
        String[][] faqs = {
            {"Why is my score low even though I have good sales?",
             "A good sales volume is important, but it's only 30% of your score. You might have a low score due to other factors:\n\n" +
             "1. **High bounce rate**: If many transactions fail, it significantly impacts your score.\n" +
             "2. **Inconsistent volumes**: Large month-to-month variations reduce your consistency score.\n" +
             "3. **Customer concentration**: If a few customers make up most of your revenue, it's risky.\n" +
             "4. **Declining trend**: If your recent months are lower than previous months, growth score suffers.\n\n" +
             "I'd need to see your specific metrics to identify exactly which factor is affecting you most."},
            
            {"How long does it take to improve my score?",
             "Score improvements typically take 30-90 days to reflect, depending on the factor:\n\n" +
             "**Quick improvements (30 days)**:\n" +
             "- Reducing bounce rate (immediate impact once fixed)\n" +
             "- Increasing transaction volume\n\n" +
             "**Medium-term (60 days)**:\n" +
             "- Improving consistency (needs 2-3 months of steady data)\n" +
             "- Diversifying customers\n\n" +
             "**Longer-term (90 days)**:\n" +
             "- Showing sustained growth trend\n" +
             "- Building transaction history\n\n" +
             "The scoring system uses your last 6 months of data, so older poor performance gradually phases out."},
            
            {"What happens if I'm rejected?",
             "If your loan application is rejected, here's what happens:\n\n" +
             "1. **You'll get a clear reason**: We tell you exactly which criteria wasn't met.\n\n" +
             "2. **You'll get improvement advice**: Specific steps to become eligible.\n\n" +
             "3. **No penalty**: Rejection doesn't hurt your score or future applications.\n\n" +
             "4. **You can reapply**: Once you address the issue (usually 30-60 days), you can apply again.\n\n" +
             "Common rejection reasons and fixes:\n" +
             "- Low volume → Increase UPI transactions\n" +
             "- High bounce rate → Work on payment success\n" +
             "- Insufficient history → Wait until you have 3+ months of data"},
            
            {"Is my data safe?",
             "Yes, your data is protected with bank-grade security:\n\n" +
             "**Data Protection**:\n" +
             "- All data encrypted (AES-256)\n" +
             "- Stored only in India (data localization)\n" +
             "- No data sold to third parties\n\n" +
             "**What we access**:\n" +
             "- Only UPI transaction metadata\n" +
             "- No personal messages or contacts\n" +
             "- No location tracking\n\n" +
             "**Compliance**:\n" +
             "- RBI guidelines compliant\n" +
             "- Digital Personal Data Protection Act compliant\n" +
             "- Regular security audits\n\n" +
             "You can request data deletion at any time."},
            
            {"Why do you only use UPI data?",
             "We use UPI transaction data because it's the most reliable indicator of business health:\n\n" +
             "**Benefits of UPI data**:\n" +
             "1. **Objective**: Real transactions, not self-reported income\n" +
             "2. **Current**: Shows your recent business activity\n" +
             "3. **Verifiable**: Directly from bank records\n" +
             "4. **Inclusive**: Available to any merchant with a bank account\n\n" +
             "**Why not traditional factors**:\n" +
             "- Credit bureau scores: Many small merchants have no credit history\n" +
             "- Income documents: Often unavailable or informal\n" +
             "- Collateral: Excludes those without assets\n\n" +
             "UPI data lets us assess any merchant fairly based on actual business performance."},
            
            {"Can I see the exact rules used to calculate my score?",
             "Yes! OpenCredit is fully transparent. You can:\n\n" +
             "1. **View the complete scoring methodology** at /api/v1/rules/methodology\n\n" +
             "2. **See all rule definitions** at /api/v1/rules/full\n\n" +
             "3. **Check the rule version** used for your assessment\n\n" +
             "4. **Review the source code** on our GitHub repository\n\n" +
             "We believe in complete transparency. Our scoring rules are open-source and community-governed. " +
             "Anyone can review them, suggest improvements, and verify there's no discrimination."},
        };

        for (String[] faq : faqs) {
            examples.add(createExample("faq", faq[0], "", faq[1]));
        }

        return examples;
    }

    /**
     * Generate edge case examples.
     */
    private List<TrainingExample> generateEdgeCaseExamples() {
        List<TrainingExample> examples = new ArrayList<>();

        // New business
        examples.add(createExample(
            "edge_case",
            "I just started my business 1 month ago. Can I get a loan?",
            "",
            """
            Unfortunately, you'll need to wait a bit longer. Here's why:

            **Current Situation**:
            - You have 1 month of transaction history
            - Minimum required: 3 months

            **Why 3 months?**
            We need enough data to fairly assess your business:
            - One month could be unusually good or bad
            - 3 months shows a pattern
            - It protects both you and the lender

            **What to do now**:
            1. Continue accepting UPI payments for all transactions
            2. Focus on building consistent monthly volume
            3. Keep your bounce rate low
            4. Come back in 2 months!

            **Good news**: Once you have 3 months of history, you'll be evaluated on your actual business performance, not assumptions about new businesses.
            """
        ));

        // Seasonal business
        examples.add(createExample(
            "edge_case",
            "My business is seasonal. Sales are very high during festivals but low otherwise. How does this affect my score?",
            "",
            """
            Seasonal businesses are common, and our scoring system accounts for this:

            **How we handle seasonality**:
            1. We look at 6 months of data to capture full cycles
            2. Consistency scoring has a "seasonal adjustment factor"
            3. We compare similar periods (this Diwali vs last Diwali if data available)

            **Tips for seasonal businesses**:

            **During peak season**:
            - Maximize UPI transactions (builds your volume score)
            - Keep bounce rate low even with high volumes
            - Don't apply for loans at peak - inflated volume won't sustain

            **During off-season**:
            - Maintain some minimum activity
            - This is actually a good time to apply for loans
            - Your "average" will be more realistic

            **Best time to apply**:
            Apply during a normal month, not peak or trough. This gives a realistic assessment of your sustainable repayment capacity.

            Your score will reflect your average capacity, which is fairer for loan sizing.
            """
        ));

        // Very high volume but poor other metrics
        examples.add(createExample(
            "edge_case",
            "I have ₹10 lakh monthly volume but my score is only 55. Why?",
            "Monthly Volume: ₹10,00,000\nBounce Rate: 18%\nCustomer Concentration: 65%\nGrowth Rate: -15%",
            """
            Despite your impressive ₹10 lakh monthly volume, other factors are significantly impacting your score. Let me break it down:

            **Your Volume Score: 100/100** ✓
            - ₹10 lakh puts you in the highest tier
            - This contributes 30 points to your score

            **Your Bounce Rate: 35/100** ⚠️
            - 18% bounce rate is concerning
            - Threshold for excellent: 3%
            - This contributes only 5.25 points (15% weight)
            - **This is your biggest problem**

            **Customer Concentration: 30/100** ⚠️
            - 65% from top 10 customers is very risky
            - If one big customer leaves, your business suffers
            - This contributes only 4.5 points

            **Growth Rate: 35/100** ⚠️
            - -15% decline is a red flag
            - Shows business may be struggling
            - This contributes only 5.25 points

            **Your total: ~55/100**

            **Priority actions**:
            1. **Fix bounce rate immediately** - 18% means nearly 1 in 5 payments fail
            2. **Acquire more customers** - Reduce dependence on top 10
            3. **Reverse decline** - Even flat growth would help

            High volume alone doesn't make a business creditworthy - stability and reliability matter too.
            """
        ));

        return examples;
    }

    /**
     * Generate multi-turn conversation examples.
     */
    private List<TrainingExample> generateConversationExamples() {
        List<TrainingExample> examples = new ArrayList<>();

        // Conversation about improving score
        examples.add(createExample(
            "conversation",
            "I want to improve my score from 65 to 80. What should I focus on?",
            "Current Score: 65, Volume: ₹1.2L, Bounce Rate: 12%, Concentration: 40%",
            """
            Great goal! To go from 65 to 80, you need to gain 15 points. Here's your prioritized action plan:

            **Quick Win - Bounce Rate (Potential: +6 points)**
            Your 12% bounce rate is costing you. Reducing it to 5% would boost your bounce score from ~50 to ~75.
            - Action: Identify why payments fail and address root causes
            - Timeline: 30 days

            **Medium-term - Customer Diversification (Potential: +5 points)**
            40% concentration in top 10 customers is risky.
            - Action: Acquire 10-15 new regular customers
            - Target: Get concentration below 30%
            - Timeline: 60 days

            **Ongoing - Volume Growth (Potential: +4 points)**
            ₹1.2L is decent but ₹2L would significantly help.
            - Action: Promote UPI payments, add new products/services
            - Timeline: 60-90 days

            **Total potential improvement: +15 points = Score of 80** ✓

            Would you like specific tactics for any of these areas?
            """
        ));

        return examples;
    }

    // ========================================================================
    // RESPONSE GENERATORS
    // ========================================================================

    private String generateScoreExplanation(FinancialMetrics metrics, ScoringResult result) {
        int score = result.getCreditScore();
        String category = result.getRiskCategory();
        
        StringBuilder sb = new StringBuilder();
        
        if (score >= 80) {
            sb.append("Great news! Your credit score of ").append(score)
              .append(" is excellent, placing you in the LOW risk category. ");
            sb.append("This means you qualify for the best loan terms available.\n\n");
        } else if (score >= 60) {
            sb.append("Your credit score of ").append(score)
              .append(" is good, placing you in the MEDIUM risk category. ");
            sb.append("You're eligible for loans with standard terms.\n\n");
        } else {
            sb.append("Your credit score of ").append(score)
              .append(" places you in the HIGH risk category. ");
            sb.append("While you may still be eligible for a loan, terms will be conservative.\n\n");
        }

        sb.append("**What's working well:**\n");
        for (String strength : result.getStrengths()) {
            sb.append("✓ ").append(strength).append("\n");
        }

        if (!result.getWarnings().isEmpty()) {
            sb.append("\n**Areas for improvement:**\n");
            for (String warning : result.getWarnings()) {
                sb.append("• ").append(warning).append("\n");
            }
        }

        return sb.toString();
    }

    private String generateScoreReasoning(FinancialMetrics metrics, ScoringResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Your score of ").append(result.getCreditScore())
          .append(" is calculated using this formula:\n\n");
        sb.append("```\n");
        sb.append("Score = (Volume × 30%) + (Consistency × 25%) + (Growth × 15%)\n");
        sb.append("      + (Bounce Rate × 15%) + (Concentration × 15%)\n");
        sb.append("```\n\n");
        
        sb.append("**Your breakdown:**\n\n");
        
        if (result.getComponentScores() != null) {
            result.getComponentScores().forEach((name, comp) -> {
                double contribution = comp.getScore().multiply(comp.getWeight()).doubleValue();
                sb.append(String.format("| %-15s | %3.0f/100 | ×%.0f%% | = %5.1f pts | %s |\n",
                    formatComponentName(name),
                    comp.getScore().doubleValue(),
                    comp.getWeight().multiply(new BigDecimal("100")).doubleValue(),
                    contribution,
                    comp.getLabel() != null ? comp.getLabel() : ""
                ));
            });
        }
        
        sb.append("\n**Total: ").append(result.getCreditScore()).append(" points**");
        
        return sb.toString();
    }

    private String generateEligibilityResponse(EligibilityResult eligibility, LoanParametersResult loanParams) {
        if (eligibility.isEligible()) {
            return String.format("""
                **Yes, this merchant is eligible for a loan!** ✓

                **Loan Terms Available:**
                - Maximum Amount: ₹%s
                - Maximum Tenure: %d days
                - Interest Rate: %.1f%% per annum

                **All criteria met:**
                ✓ Monthly volume above ₹25,000 minimum
                ✓ At least 20 transactions on record
                ✓ Bounce rate within acceptable limits
                ✓ Sufficient business history
                ✓ No fraud indicators

                The merchant can proceed with the loan application.
                """,
                formatCurrency(loanParams.getEligibleAmount()),
                loanParams.getMaxTenureDays(),
                loanParams.getInterestRateAnnual().doubleValue()
            );
        } else {
            return String.format("""
                **This merchant is currently not eligible for a loan.**

                **Reason:** %s

                **Recommendation:** %s

                **Next steps:**
                1. Address the issue mentioned above
                2. Maintain good transaction patterns for 30 days
                3. Request a new assessment

                This is not a permanent rejection - eligibility can be achieved by improving the specific factor mentioned.
                """,
                eligibility.getFailureReason(),
                eligibility.getRecommendation() != null ? eligibility.getRecommendation() : "Work on improving the failed criteria."
            );
        }
    }

    private String generateRecommendations(FinancialMetrics metrics, ScoringResult result) {
        List<String> recommendations = new ArrayList<>();
        
        // Analyze each metric and provide specific advice
        if (metrics.getBounceRate().compareTo(new BigDecimal("5")) > 0) {
            recommendations.add("""
                **Reduce your bounce rate** (currently %.1f%%)
                - Send payment reminders before due dates
                - Verify customer funds for large transactions
                - Follow up immediately on failed payments
                - Target: Below 5%% for best score impact
                """.formatted(metrics.getBounceRate().doubleValue()));
        }
        
        if (metrics.getCustomerConcentration().compareTo(new BigDecimal("30")) > 0) {
            recommendations.add("""
                **Diversify your customer base** (top 10 = %.1f%% of revenue)
                - Focus on acquiring new customers
                - Offer UPI payment incentives to new customers
                - Don't rely too heavily on a few big customers
                - Target: No customer > 20%% of your revenue
                """.formatted(metrics.getCustomerConcentration().doubleValue()));
        }
        
        if (metrics.getGrowthRate().compareTo(new BigDecimal("10")) < 0) {
            recommendations.add("""
                **Improve your growth trajectory** (currently %.1f%%)
                - Analyze why sales may be declining
                - Consider promotional offers
                - Expand your product/service range
                - Target: Positive growth, ideally 10%%+
                """.formatted(metrics.getGrowthRate().doubleValue()));
        }
        
        if (metrics.getAverageMonthlyVolume().compareTo(new BigDecimal("200000")) < 0) {
            recommendations.add("""
                **Increase transaction volume** (currently ₹%s/month)
                - Encourage all customers to pay via UPI
                - Accept UPI for even small transactions
                - Display your QR code prominently
                - Target: ₹2 lakh+ monthly for good score
                """.formatted(formatCurrency(metrics.getAverageMonthlyVolume())));
        }

        if (recommendations.isEmpty()) {
            return """
                Excellent! Your metrics are strong across all areas. To maintain your score:
                - Continue your current business practices
                - Stay consistent month-over-month
                - Keep bounce rate low
                - Monitor for any declining trends
                
                You're in great shape for favorable loan terms!
                """;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Here are your priority improvements, ranked by potential impact:\n\n");
        for (int i = 0; i < recommendations.size(); i++) {
            sb.append(i + 1).append(". ").append(recommendations.get(i)).append("\n");
        }
        sb.append("\n*Focus on these for 30-60 days, then request a reassessment.*");
        
        return sb.toString();
    }

    private String generateRiskAnalysis(FinancialMetrics metrics, ScoringResult result, LoanParametersResult loanParams) {
        return String.format("""
            ## Risk Analysis Report
            
            **Risk Category:** %s
            **Credit Score:** %d/100
            
            ### Key Risk Indicators
            
            | Factor | Value | Risk Level |
            |--------|-------|------------|
            | Bounce Rate | %.1f%% | %s |
            | Concentration | %.1f%% | %s |
            | Growth Trend | %.1f%% | %s |
            | Volume | ₹%s | %s |
            
            ### Strengths
            %s
            
            ### Concerns
            %s
            
            ### Recommended Loan Structure
            - **Amount:** ₹%s (%.0f%% of monthly volume)
            - **Tenure:** %d days
            - **Rate:** %.1f%% p.a.
            
            ### Lending Recommendation
            %s
            """,
            result.getRiskCategory(),
            result.getCreditScore(),
            metrics.getBounceRate().doubleValue(),
            metrics.getBounceRate().compareTo(new BigDecimal("10")) > 0 ? "⚠️ HIGH" : "✓ LOW",
            metrics.getCustomerConcentration().doubleValue(),
            metrics.getCustomerConcentration().compareTo(new BigDecimal("50")) > 0 ? "⚠️ HIGH" : "✓ LOW",
            metrics.getGrowthRate().doubleValue(),
            metrics.getGrowthRate().compareTo(BigDecimal.ZERO) < 0 ? "⚠️ DECLINING" : "✓ STABLE/GROWING",
            formatCurrency(metrics.getAverageMonthlyVolume()),
            metrics.getAverageMonthlyVolume().compareTo(new BigDecimal("100000")) >= 0 ? "✓ ADEQUATE" : "⚠️ LOW",
            result.getStrengths().isEmpty() ? "None identified" : String.join("\n", result.getStrengths().stream().map(s -> "- " + s).toList()),
            result.getWarnings().isEmpty() ? "None identified" : String.join("\n", result.getWarnings().stream().map(w -> "- " + w).toList()),
            formatCurrency(loanParams.getEligibleAmount()),
            loanParams.getVolumeMultiplier().multiply(new BigDecimal("100")).doubleValue(),
            loanParams.getMaxTenureDays(),
            loanParams.getInterestRateAnnual().doubleValue(),
            getLendingRecommendation(result.getRiskCategory())
        );
    }

    private String getLendingRecommendation(String riskCategory) {
        return switch (riskCategory) {
            case "LOW" -> "**APPROVE** - Low risk profile. Recommend standard processing with favorable terms.";
            case "MEDIUM" -> "**APPROVE WITH MONITORING** - Acceptable risk. Standard terms recommended with periodic review.";
            case "HIGH" -> "**MANUAL REVIEW** - Higher risk profile. Consider additional verification or reduced terms.";
            default -> "**REVIEW REQUIRED** - Additional assessment needed.";
        };
    }

    private String generateBreakdown(ScoringResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Credit Score Component Breakdown\n\n");
        sb.append("Your score of **").append(result.getCreditScore()).append("/100** is composed of:\n\n");
        sb.append("| Component | Score | Weight | Contribution | Rating |\n");
        sb.append("|-----------|-------|--------|--------------|--------|\n");
        
        if (result.getComponentScores() != null) {
            result.getComponentScores().forEach((name, comp) -> {
                sb.append(String.format("| %s | %.0f/100 | %.0f%% | %.1f pts | %s |\n",
                    formatComponentName(name),
                    comp.getScore().doubleValue(),
                    comp.getWeight().multiply(new BigDecimal("100")).doubleValue(),
                    comp.getScore().multiply(comp.getWeight()).doubleValue(),
                    comp.getLabel() != null ? comp.getLabel() : "-"
                ));
            });
        }
        
        sb.append("\n**Total: ").append(result.getCreditScore()).append(" points**");
        return sb.toString();
    }

    private String generateBounceAnalysis(FinancialMetrics metrics) {
        double rate = metrics.getBounceRate().doubleValue();
        String severity = rate > 15 ? "very concerning" : rate > 10 ? "concerning" : "moderate";
        
        return String.format("""
            Your bounce rate of %.1f%% is %s.
            
            **What this means:**
            - %.1f%% of your transactions are failing
            - This indicates potential payment collection issues
            - Lenders see this as a risk indicator
            
            **Common causes:**
            - Customers with insufficient funds
            - Technical payment issues
            - Incorrect payment details
            
            **Impact on your score:**
            - Bounce rate accounts for 15%% of your score
            - Your current rate gives you approximately %.0f/100 in this component
            - Reducing to 5%% would give you 75/100 (+%.0f points potential)
            
            **How to improve:**
            1. Send payment reminders before due dates
            2. Verify funds for large transactions
            3. Offer multiple payment options
            4. Follow up quickly on failures
            
            **Target:** Below 5%% for excellent rating
            """,
            rate, severity, rate,
            calculateBounceScore(rate),
            75 - calculateBounceScore(rate)
        );
    }

    private double calculateBounceScore(double rate) {
        if (rate <= 3) return 100;
        if (rate <= 7) return 75;
        if (rate <= 12) return 50;
        if (rate <= 20) return 25;
        return 0;
    }

    private String generateConcentrationAnalysis(FinancialMetrics metrics) {
        double conc = metrics.getCustomerConcentration().doubleValue();
        
        return String.format("""
            Your customer concentration of %.1f%% means your top 10 customers represent %.1f%% of your revenue.
            
            **Why this matters:**
            - High concentration = High business risk
            - If one big customer leaves, your revenue drops significantly
            - Lenders prefer diversified customer bases
            
            **Impact on your score:**
            - Concentration accounts for 15%% of your score
            - Your current concentration gives you approximately %.0f/100
            - Reducing to 25%% would give you ~80/100
            
            **How to improve:**
            1. Focus on acquiring new customers
            2. Offer incentives for new customer referrals
            3. Expand to new market segments
            4. Don't give excessive credit to big customers
            
            **Target:** Top 10 customers < 30%% of revenue
            """,
            conc, conc,
            conc <= 20 ? 100 : conc <= 35 ? 75 : conc <= 50 ? 50 : conc <= 70 ? 30 : 20
        );
    }

    private String generateGrowthAnalysis(FinancialMetrics metrics) {
        double growth = metrics.getGrowthRate().doubleValue();
        
        return String.format("""
            Your growth rate of %.1f%% indicates a declining trend in your business.
            
            **What this means:**
            - Your recent 3-month volume is %.1f%% lower than the previous 3 months
            - This could indicate business challenges
            - Lenders are cautious about declining businesses
            
            **Possible causes:**
            - Seasonal factors (may not be concerning)
            - Market competition
            - Customer churn
            - Economic conditions
            
            **Impact on your score:**
            - Growth accounts for 15%% of your score
            - Negative growth typically scores 20-35/100
            - Even flat growth (0%%) would improve your score
            
            **How to reverse the trend:**
            1. Analyze why sales are declining
            2. Re-engage with past customers
            3. Consider promotional offers
            4. Explore new products or services
            5. Increase marketing efforts
            
            **Target:** Positive growth (10%%+ is excellent)
            """,
            growth, Math.abs(growth)
        );
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private FinancialMetrics generateRealisticMetrics() {
        // Generate diverse profiles covering all score ranges
        BigDecimal volume = randomDecimal(15000, 800000);
        BigDecimal bounceRate = randomDecimal(0, 22);
        BigDecimal concentration = randomDecimal(15, 75);
        BigDecimal growthRate = randomDecimal(-30, 45);
        BigDecimal consistency = randomDecimal(30, 95);
        int transactions = random.nextInt(400) + 15;
        int customers = random.nextInt(80) + 5;

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
            .averageTransactionValue(volume.divide(new BigDecimal(Math.max(1, transactions / 30)), 2, RoundingMode.HALF_UP))
            .build();
    }

    private BigDecimal randomDecimal(double min, double max) {
        return new BigDecimal(min + (max - min) * random.nextDouble())
            .setScale(2, RoundingMode.HALF_UP);
    }

    private String buildMerchantContext(FinancialMetrics metrics, ScoringResult result) {
        return String.format("""
            Merchant Profile:
            - Credit Score: %d/100 (%s Risk)
            - Monthly Volume: ₹%s
            - Transaction Count: %d
            - Unique Customers: %d
            - Bounce Rate: %.1f%%
            - Customer Concentration: %.1f%%
            - Growth Rate: %.1f%%
            - Consistency: %.1f/100
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

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0";
        double val = amount.doubleValue();
        if (val >= 100000) return String.format("%.2fL", val / 100000);
        if (val >= 1000) return String.format("%.1fK", val / 1000);
        return String.format("%.0f", val);
    }

    private String formatComponentName(String name) {
        return switch (name) {
            case "volume" -> "Volume";
            case "consistency" -> "Consistency";
            case "growth" -> "Growth";
            case "bounce_rate" -> "Bounce Rate";
            case "concentration" -> "Concentration";
            default -> name.substring(0, 1).toUpperCase() + name.substring(1).replace("_", " ");
        };
    }

    private TrainingExample createExample(String category, String instruction, String input, String output) {
        return TrainingExample.builder()
            .category(category)
            .instruction(instruction)
            .input(input)
            .output(output)
            .build();
    }

    private Map<String, Integer> countCategories(List<TrainingExample> examples) {
        return examples.stream()
            .collect(Collectors.groupingBy(TrainingExample::getCategory, Collectors.summingInt(e -> 1)));
    }

    // ========================================================================
    // OUTPUT WRITERS
    // ========================================================================

    private void writeOpenAIFormat(List<TrainingExample> examples, Path path) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            for (TrainingExample ex : examples) {
                Map<String, Object> entry = Map.of(
                    "messages", List.of(
                        Map.of("role", "system", "content", getSystemPrompt()),
                        Map.of("role", "user", "content", 
                            ex.getInput().isEmpty() ? ex.getInstruction() : ex.getInstruction() + "\n\n" + ex.getInput()),
                        Map.of("role", "assistant", "content", ex.getOutput())
                    )
                );
                writer.write(jsonMapper.writeValueAsString(entry));
                writer.newLine();
            }
        }
        log.info("Wrote OpenAI format: {} examples to {}", examples.size(), path);
    }

    private void writeAlpacaFormat(List<TrainingExample> examples, Path path) throws IOException {
        List<Map<String, String>> data = examples.stream()
            .map(ex -> Map.of(
                "instruction", ex.getInstruction(),
                "input", ex.getInput(),
                "output", ex.getOutput()
            ))
            .toList();
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), data);
        log.info("Wrote Alpaca format: {} examples to {}", examples.size(), path);
    }

    private void writeLlamaFormat(List<TrainingExample> examples, Path path) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            for (TrainingExample ex : examples) {
                String text = String.format("<s>[INST] <<SYS>>\\n%s\\n<</SYS>>\\n\\n%s [/INST] %s </s>",
                    getSystemPrompt(),
                    ex.getInput().isEmpty() ? ex.getInstruction() : ex.getInstruction() + "\\n\\n" + ex.getInput(),
                    ex.getOutput()
                );
                writer.write(jsonMapper.writeValueAsString(Map.of("text", text)));
                writer.newLine();
            }
        }
        log.info("Wrote Llama format: {} examples to {}", examples.size(), path);
    }

    private void writeHuggingFaceFormat(List<TrainingExample> examples, Path path) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            for (TrainingExample ex : examples) {
                Map<String, Object> entry = Map.of(
                    "instruction", ex.getInstruction(),
                    "input", ex.getInput(),
                    "output", ex.getOutput(),
                    "category", ex.getCategory()
                );
                writer.write(jsonMapper.writeValueAsString(entry));
                writer.newLine();
            }
        }
        log.info("Wrote HuggingFace format: {} examples to {}", examples.size(), path);
    }

    private String getSystemPrompt() {
        return """
            You are a credit advisor AI for OpenCredit, an open-source credit scoring platform for small merchants in India.
            
            Your role is to EXPLAIN credit scores and decisions - NOT to calculate them. All calculations are done by a deterministic rule engine.
            
            Key principles:
            1. Be clear and helpful to small business owners who may not be financial experts
            2. Use simple language, avoid jargon
            3. Always be encouraging - help them understand how to improve
            4. Be honest about limitations and concerns
            5. Use Indian currency format (₹, lakhs, crores)
            6. Reference the transparent, open-source nature of the scoring rules
            
            The scoring system uses only UPI transaction data and evaluates:
            - Transaction Volume (30% weight)
            - Consistency (25% weight)
            - Growth Rate (15% weight)
            - Bounce Rate (15% weight)
            - Customer Concentration (15% weight)
            
            Never use discriminatory factors like gender, religion, caste, or location.
            """;
    }

    // ========================================================================
    // DATA CLASSES
    // ========================================================================

    @Data
    @Builder
    public static class TrainingExample {
        private String category;
        private String instruction;
        private String input;
        private String output;
    }

    @Data
    @Builder
    public static class TrainingStats {
        private int totalExamples;
        private int trainExamples;
        private int valExamples;
        private int testExamples;
        private Map<String, Integer> categoryCounts;
    }
}
