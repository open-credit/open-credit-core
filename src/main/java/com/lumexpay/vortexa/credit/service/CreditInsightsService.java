package com.lumexpay.vortexa.credit.service;


import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.lumexpay.vortexa.credit.client.LlmClient;
import com.lumexpay.vortexa.credit.config.LlmConfig;
import com.lumexpay.vortexa.credit.dto.FinancialMetrics;
import com.lumexpay.vortexa.credit.model.CreditAssessment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Service for generating AI-powered credit insights using FinGPT/LLM.
 * Provides natural language explanations, recommendations, and risk narratives.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CreditInsightsService {

    private final LlmClient llmClient;
    private final LlmConfig llmConfig;

    private static final String SYSTEM_PROMPT = """
        You are FinGPT, an expert financial analyst AI specializing in merchant credit assessment 
        for the Indian market. You analyze UPI transaction data to provide insights about 
        merchant creditworthiness.
        
        Your responses should be:
        - Professional and clear
        - Specific to the data provided
        - Actionable with concrete recommendations
        - Appropriate for both lenders and merchants
        - Using Indian Rupee (₹) for currency
        - Considering Indian business context (festivals, seasonal patterns, etc.)
        
        Always maintain a balanced view, highlighting both strengths and areas of concern.
        """;

    /**
     * Generate comprehensive credit insights for a merchant.
     */
    public CreditInsights generateInsights(CreditAssessment assessment, FinancialMetrics metrics) {
        log.info("Generating AI insights for merchant: {}", assessment.getMerchantId());

        if (!llmConfig.isEnabled()) {
            return generateBasicInsights(assessment);
        }

        try {
            String creditNarrative = generateCreditNarrative(assessment, metrics);
            String riskAnalysis = generateRiskAnalysis(assessment, metrics);
            String recommendations = generateRecommendations(assessment, metrics);
            String executiveSummary = generateExecutiveSummary(assessment, metrics);
            String improvementPlan = generateImprovementPlan(assessment, metrics);

            return CreditInsights.builder()
                .merchantId(assessment.getMerchantId())
                .creditScore(assessment.getCreditScore())
                .executiveSummary(executiveSummary)
                .creditNarrative(creditNarrative)
                .riskAnalysis(riskAnalysis)
                .recommendations(recommendations)
                .improvementPlan(improvementPlan)
                .aiGenerated(true)
                .build();

        } catch (Exception e) {
            log.error("Failed to generate AI insights: {}", e.getMessage(), e);
            return generateBasicInsights(assessment);
        }
    }

    /**
     * Generate a natural language narrative explaining the credit score.
     */
    public String generateCreditNarrative(CreditAssessment assessment, FinancialMetrics metrics) {
        if (!llmConfig.getFeatures().isCreditInsights()) {
            return "";
        }

        String prompt = String.format("""
            Analyze this merchant's credit profile and explain their credit score in 2-3 paragraphs:
            
            **Credit Score:** %d/100 (%s Risk)
            
            **Financial Metrics:**
            - Average Monthly Volume: ₹%s
            - Last 3 Months Volume: ₹%s
            - Transaction Count: %d
            - Unique Customers: %d
            
            **Performance Metrics:**
            - Consistency Score: %.1f/100
            - Growth Rate: %.1f%%
            - Bounce Rate: %.1f%%
            - Customer Concentration: %.1f%% (top 10 customers)
            
            **Component Scores:**
            - Volume Score: %.0f
            - Growth Score: %.0f
            - Bounce Rate Score: %.0f
            - Concentration Score: %.0f
            
            Explain what these numbers mean for the merchant's creditworthiness and why they 
            received this score. Use simple language a small business owner would understand.
            """,
            assessment.getCreditScore(),
            assessment.getRiskCategory().name(),
            formatIndianCurrency(assessment.getAverageMonthlyVolume()),
            formatIndianCurrency(assessment.getLast3MonthsVolume()),
            assessment.getTransactionCount(),
            assessment.getUniqueCustomerCount(),
            assessment.getConsistencyScore().doubleValue(),
            assessment.getGrowthRate().doubleValue(),
            assessment.getBounceRate().doubleValue(),
            assessment.getCustomerConcentration().doubleValue(),
            assessment.getVolumeScore().doubleValue(),
            assessment.getGrowthScore().doubleValue(),
            assessment.getBounceRateScore().doubleValue(),
            assessment.getConcentrationScore().doubleValue()
        );

        return llmClient.generateCompletion(prompt, SYSTEM_PROMPT);
    }

    /**
     * Generate detailed risk analysis.
     */
    public String generateRiskAnalysis(CreditAssessment assessment, FinancialMetrics metrics) {
        if (!llmConfig.getFeatures().isRiskNarrative()) {
            return "";
        }

        String prompt = String.format("""
            Provide a detailed risk analysis for this merchant loan application:
            
            **Risk Category:** %s
            **Credit Score:** %d/100
            **Eligible Loan Amount:** ₹%s
            **Recommended Interest Rate:** %.1f%% p.a.
            **Maximum Tenure:** %d days
            
            **Risk Indicators:**
            - Bounce Rate: %.1f%% %s
            - Customer Concentration: %.1f%% %s
            - Growth Trend: %.1f%% %s
            - Consistency: %.1f/100 %s
            
            **Warnings:** %s
            **Strengths:** %s
            
            Analyze:
            1. Key risk factors that lenders should consider
            2. Mitigating factors that reduce risk
            3. Recommended loan structure (amount, tenure, repayment frequency)
            4. Red flags or concerns
            5. Overall lending recommendation
            """,
            assessment.getRiskCategory().getDescription(),
            assessment.getCreditScore(),
            formatIndianCurrency(assessment.getEligibleLoanAmount()),
            assessment.getRecommendedInterestRate().doubleValue(),
            assessment.getMaxTenureDays(),
            assessment.getBounceRate().doubleValue(),
            assessment.getBounceRate().compareTo(new BigDecimal("10")) > 0 ? "(HIGH)" : "(ACCEPTABLE)",
            assessment.getCustomerConcentration().doubleValue(),
            assessment.getCustomerConcentration().compareTo(new BigDecimal("50")) > 0 ? "(HIGH)" : "(ACCEPTABLE)",
            assessment.getGrowthRate().doubleValue(),
            assessment.getGrowthRate().compareTo(BigDecimal.ZERO) < 0 ? "(DECLINING)" : "(GROWING)",
            assessment.getConsistencyScore().doubleValue(),
            assessment.getConsistencyScore().compareTo(new BigDecimal("60")) < 0 ? "(VOLATILE)" : "(STABLE)",
            String.join(", ", assessment.getWarnings()),
            String.join(", ", assessment.getStrengths())
        );

        return llmClient.generateCompletion(prompt, SYSTEM_PROMPT);
    }

    /**
     * Generate personalized recommendations for the merchant.
     */
    public String generateRecommendations(CreditAssessment assessment, FinancialMetrics metrics) {
        if (!llmConfig.getFeatures().isRecommendations()) {
            return "";
        }

        String prompt = String.format("""
            Generate personalized recommendations for this merchant to improve their credit profile:
            
            **Current Status:**
            - Credit Score: %d/100 (%s Risk)
            - Eligible: %s
            %s
            
            **Key Metrics to Improve:**
            - Monthly Volume: ₹%s (Target: ₹5,00,000+ for excellent)
            - Bounce Rate: %.1f%% (Target: <5%% for excellent)
            - Customer Concentration: %.1f%% (Target: <30%% for excellent)
            - Consistency Score: %.1f (Target: >80 for excellent)
            - Growth Rate: %.1f%%
            
            **Current Warnings:** %s
            
            Provide 5-7 specific, actionable recommendations that would:
            1. Improve their credit score
            2. Increase their loan eligibility
            3. Reduce their risk profile
            4. Strengthen their business fundamentals
            
            Format as numbered recommendations with brief explanations.
            Consider Indian market context (UPI adoption, digital payments, seasonal festivals).
            """,
            assessment.getCreditScore(),
            assessment.getRiskCategory().name(),
            assessment.getIsEligible() ? "Yes" : "No",
            assessment.getIsEligible() ? "" : "Reason: " + assessment.getIneligibilityReason(),
            formatIndianCurrency(assessment.getAverageMonthlyVolume()),
            assessment.getBounceRate().doubleValue(),
            assessment.getCustomerConcentration().doubleValue(),
            assessment.getConsistencyScore().doubleValue(),
            assessment.getGrowthRate().doubleValue(),
            String.join(", ", assessment.getWarnings())
        );

        return llmClient.generateCompletion(prompt, SYSTEM_PROMPT);
    }

    /**
     * Generate executive summary for reports.
     */
    public String generateExecutiveSummary(CreditAssessment assessment, FinancialMetrics metrics) {
        String prompt = String.format("""
            Write a concise executive summary (3-4 sentences) for this credit assessment:
            
            Merchant: %s
            Credit Score: %d/100
            Risk Category: %s
            Eligible: %s
            Eligible Amount: ₹%s
            Average Monthly Volume: ₹%s
            Key Strength: %s
            Key Concern: %s
            
            The summary should be suitable for a lending committee review.
            """,
            assessment.getMerchantId(),
            assessment.getCreditScore(),
            assessment.getRiskCategory().getDescription(),
            assessment.getIsEligible() ? "Yes" : "No",
            formatIndianCurrency(assessment.getEligibleLoanAmount()),
            formatIndianCurrency(assessment.getAverageMonthlyVolume()),
            assessment.getStrengths().isEmpty() ? "N/A" : assessment.getStrengths().get(0),
            assessment.getWarnings().isEmpty() ? "None" : assessment.getWarnings().get(0)
        );

        return llmClient.generateCompletion(prompt, SYSTEM_PROMPT);
    }

    /**
     * Generate a 30-60-90 day improvement plan.
     */
    public String generateImprovementPlan(CreditAssessment assessment, FinancialMetrics metrics) {
        if (!assessment.getIsEligible() || assessment.getCreditScore() < 80) {
            String prompt = String.format("""
                Create a 30-60-90 day improvement plan for this merchant:
                
                Current Credit Score: %d/100
                Target Score: 80+ (to achieve LOW risk status)
                
                Current Issues:
                - Bounce Rate: %.1f%% (needs to be <5%%)
                - Customer Concentration: %.1f%% (needs to be <30%%)
                - Monthly Volume: ₹%s (needs to be >₹5L for excellent)
                - Consistency: %.1f/100 (needs to be >80)
                
                Create a practical action plan with:
                
                **Days 1-30 (Quick Wins):**
                - Focus on immediate improvements
                
                **Days 31-60 (Foundation Building):**
                - Structural changes to business practices
                
                **Days 61-90 (Sustained Growth):**
                - Long-term improvements
                
                Include specific, measurable actions appropriate for a small Indian merchant.
                """,
                assessment.getCreditScore(),
                assessment.getBounceRate().doubleValue(),
                assessment.getCustomerConcentration().doubleValue(),
                formatIndianCurrency(assessment.getAverageMonthlyVolume()),
                assessment.getConsistencyScore().doubleValue()
            );

            return llmClient.generateCompletion(prompt, SYSTEM_PROMPT);
        }
        return "Congratulations! Your credit profile is already excellent. Continue maintaining your current business practices.";
    }

    /**
     * Explain a specific anomaly or pattern in the data.
     */
    public String explainAnomaly(String anomalyType, FinancialMetrics metrics) {
        if (!llmConfig.getFeatures().isAnomalyExplanation()) {
            return "";
        }

        String prompt = String.format("""
            Explain this detected anomaly in the merchant's transaction data:
            
            **Anomaly Type:** %s
            
            **Context:**
            - Monthly volumes varied from ₹%s to ₹%s
            - Coefficient of Variation: %.2f
            - Growth Rate: %.1f%%
            - Is Seasonal: %s
            
            Provide:
            1. What this anomaly typically indicates
            2. Possible legitimate business reasons
            3. Potential red flags to investigate
            4. How this affects credit assessment
            """,
            anomalyType,
            formatIndianCurrency(findMinVolume(metrics)),
            formatIndianCurrency(findMaxVolume(metrics)),
            metrics.getCoefficientOfVariation() != null ? metrics.getCoefficientOfVariation().doubleValue() : 0,
            metrics.getGrowthRate() != null ? metrics.getGrowthRate().doubleValue() : 0,
            Boolean.TRUE.equals(metrics.getIsSeasonalBusiness()) ? "Yes" : "No"
        );

        return llmClient.generateCompletion(prompt, SYSTEM_PROMPT);
    }

    /**
     * Generate comparative analysis against industry benchmarks.
     */
    public String generateComparativeAnalysis(CreditAssessment assessment, String industryCategory) {
        if (!llmConfig.getFeatures().isComparativeAnalysis()) {
            return "";
        }

        String prompt = String.format("""
            Compare this merchant's performance against typical %s industry benchmarks:
            
            **Merchant Metrics:**
            - Credit Score: %d/100
            - Average Monthly Volume: ₹%s
            - Bounce Rate: %.1f%%
            - Customer Concentration: %.1f%%
            - Growth Rate: %.1f%%
            
            **Industry Context:** %s merchants in India
            
            Provide:
            1. How this merchant compares to industry average (above/below/at par)
            2. Areas where they outperform peers
            3. Areas where they lag behind
            4. Industry-specific considerations
            5. Seasonality factors for this industry
            """,
            industryCategory,
            assessment.getCreditScore(),
            formatIndianCurrency(assessment.getAverageMonthlyVolume()),
            assessment.getBounceRate().doubleValue(),
            assessment.getCustomerConcentration().doubleValue(),
            assessment.getGrowthRate().doubleValue(),
            industryCategory
        );

        return llmClient.generateCompletion(prompt, SYSTEM_PROMPT);
    }

    /**
     * Generate basic insights without LLM (fallback).
     */
    private CreditInsights generateBasicInsights(CreditAssessment assessment) {
        String basicSummary = String.format(
            "Merchant %s has a credit score of %d/100, placing them in the %s risk category. " +
            "Based on their transaction history, they are %s for a loan of up to ₹%s.",
            assessment.getMerchantId(),
            assessment.getCreditScore(),
            assessment.getRiskCategory().name(),
            assessment.getIsEligible() ? "eligible" : "not eligible",
            formatIndianCurrency(assessment.getEligibleLoanAmount())
        );

        return CreditInsights.builder()
            .merchantId(assessment.getMerchantId())
            .creditScore(assessment.getCreditScore())
            .executiveSummary(basicSummary)
            .creditNarrative("AI-powered narrative unavailable. Please refer to numerical metrics.")
            .riskAnalysis("AI-powered analysis unavailable. Please refer to risk category and warnings.")
            .recommendations("AI-powered recommendations unavailable.")
            .improvementPlan("")
            .aiGenerated(false)
            .build();
    }

    /**
     * Format currency in Indian format (lakhs/crores).
     */
    private String formatIndianCurrency(BigDecimal amount) {
        if (amount == null) return "0";
        
        double value = amount.doubleValue();
        if (value >= 10000000) {
            return String.format("%.2f Cr", value / 10000000);
        } else if (value >= 100000) {
            return String.format("%.2f L", value / 100000);
        } else if (value >= 1000) {
            return String.format("%.2f K", value / 1000);
        }
        return String.format("%.0f", value);
    }

    private BigDecimal findMinVolume(FinancialMetrics metrics) {
        if (metrics.getMonthlyVolumes() == null || metrics.getMonthlyVolumes().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return metrics.getMonthlyVolumes().stream()
            .map(FinancialMetrics.MonthlyVolume::getVolume)
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
    }

    private BigDecimal findMaxVolume(FinancialMetrics metrics) {
        if (metrics.getMonthlyVolumes() == null || metrics.getMonthlyVolumes().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return metrics.getMonthlyVolumes().stream()
            .map(FinancialMetrics.MonthlyVolume::getVolume)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
    }

    /**
     * Credit Insights response model.
     */
    @Data
    @Builder
    public static class CreditInsights {
        private String merchantId;
        private Integer creditScore;
        private String executiveSummary;
        private String creditNarrative;
        private String riskAnalysis;
        private String recommendations;
        private String improvementPlan;
        private boolean aiGenerated;
    }
}
