package com.lumexpay.vortexa.credit.service;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lumexpay.vortexa.credit.client.LlmClient;
import com.lumexpay.vortexa.credit.config.LlmConfig;
import com.lumexpay.vortexa.credit.dto.FinancialMetrics;
import com.lumexpay.vortexa.credit.model.CreditAssessment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CreditInsightsService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Credit Insights Service Tests")
class CreditInsightsServiceTest {

    @Mock
    private LlmClient llmClient;

    private LlmConfig llmConfig;
    private CreditInsightsService service;

    @BeforeEach
    void setUp() {
        llmConfig = createDefaultConfig();
        service = new CreditInsightsService(llmClient, llmConfig);
    }

    @Nested
    @DisplayName("Generate Insights Tests")
    class GenerateInsightsTests {

        @Test
        @DisplayName("Should generate complete insights when LLM is enabled")
        void shouldGenerateCompleteInsights() {
            // Given
            CreditAssessment assessment = createSampleAssessment();
            FinancialMetrics metrics = createSampleMetrics();
            
            when(llmClient.generateCompletion(anyString(), anyString()))
                .thenReturn("AI generated insight text");

            // When
            CreditInsightsService.CreditInsights insights = service.generateInsights(assessment, metrics);

            // Then
            assertNotNull(insights);
            assertEquals("MERCHANT_001", insights.getMerchantId());
            assertEquals(75, insights.getCreditScore());
            assertTrue(insights.isAiGenerated());
            assertNotNull(insights.getExecutiveSummary());
            assertNotNull(insights.getCreditNarrative());
            assertNotNull(insights.getRiskAnalysis());
            assertNotNull(insights.getRecommendations());
        }

        @Test
        @DisplayName("Should generate basic insights when LLM is disabled")
        void shouldGenerateBasicInsightsWhenLlmDisabled() {
            // Given
            llmConfig.setEnabled(false);
            service = new CreditInsightsService(llmClient, llmConfig);
            
            CreditAssessment assessment = createSampleAssessment();
            FinancialMetrics metrics = createSampleMetrics();

            // When
            CreditInsightsService.CreditInsights insights = service.generateInsights(assessment, metrics);

            // Then
            assertNotNull(insights);
            assertFalse(insights.isAiGenerated());
            assertTrue(insights.getExecutiveSummary().contains("MERCHANT_001"));
            assertTrue(insights.getExecutiveSummary().contains("75"));
        }

        @Test
        @DisplayName("Should fallback to basic insights on LLM error")
        void shouldFallbackOnLlmError() {
            // Given
            CreditAssessment assessment = createSampleAssessment();
            FinancialMetrics metrics = createSampleMetrics();
            
            when(llmClient.generateCompletion(anyString(), anyString()))
                .thenThrow(new RuntimeException("LLM API Error"));

            // When
            CreditInsightsService.CreditInsights insights = service.generateInsights(assessment, metrics);

            // Then
            assertNotNull(insights);
            assertFalse(insights.isAiGenerated());
        }
    }

    @Nested
    @DisplayName("Credit Narrative Tests")
    class CreditNarrativeTests {

        @Test
        @DisplayName("Should generate narrative with correct data")
        void shouldGenerateNarrativeWithCorrectData() {
            // Given
            CreditAssessment assessment = createSampleAssessment();
            FinancialMetrics metrics = createSampleMetrics();
            
            when(llmClient.generateCompletion(argThat(prompt -> 
                prompt.contains("75") && prompt.contains("MEDIUM")), anyString()))
                .thenReturn("Credit narrative explaining the score");

            // When
            String narrative = service.generateCreditNarrative(assessment, metrics);

            // Then
            assertNotNull(narrative);
            assertEquals("Credit narrative explaining the score", narrative);
        }

        @Test
        @DisplayName("Should return empty when feature is disabled")
        void shouldReturnEmptyWhenFeatureDisabled() {
            // Given
            llmConfig.getFeatures().setCreditInsights(false);
            service = new CreditInsightsService(llmClient, llmConfig);
            
            CreditAssessment assessment = createSampleAssessment();
            FinancialMetrics metrics = createSampleMetrics();

            // When
            String narrative = service.generateCreditNarrative(assessment, metrics);

            // Then
            assertEquals("", narrative);
        }
    }

    @Nested
    @DisplayName("Risk Analysis Tests")
    class RiskAnalysisTests {

        @Test
        @DisplayName("Should generate risk analysis for lenders")
        void shouldGenerateRiskAnalysis() {
            // Given
            CreditAssessment assessment = createSampleAssessment();
            FinancialMetrics metrics = createSampleMetrics();
            
            when(llmClient.generateCompletion(argThat(prompt -> 
                prompt.contains("risk") || prompt.contains("Risk")), anyString()))
                .thenReturn("Detailed risk analysis for lenders");

            // When
            String analysis = service.generateRiskAnalysis(assessment, metrics);

            // Then
            assertNotNull(analysis);
            assertEquals("Detailed risk analysis for lenders", analysis);
        }
    }

    @Nested
    @DisplayName("Recommendations Tests")
    class RecommendationsTests {

        @Test
        @DisplayName("Should generate personalized recommendations")
        void shouldGenerateRecommendations() {
            // Given
            CreditAssessment assessment = createSampleAssessment();
            FinancialMetrics metrics = createSampleMetrics();
            
            when(llmClient.generateCompletion(anyString(), anyString()))
                .thenReturn("1. Reduce bounce rate\n2. Diversify customers");

            // When
            String recommendations = service.generateRecommendations(assessment, metrics);

            // Then
            assertNotNull(recommendations);
            assertTrue(recommendations.contains("1."));
        }

        @Test
        @DisplayName("Should include ineligibility reason for ineligible merchants")
        void shouldIncludeIneligibilityReason() {
            // Given
            CreditAssessment assessment = createIneligibleAssessment();
            FinancialMetrics metrics = createSampleMetrics();
            
            when(llmClient.generateCompletion(argThat(prompt -> 
                prompt.contains("Volume too low")), anyString()))
                .thenReturn("Recommendations for ineligible merchant");

            // When
            String recommendations = service.generateRecommendations(assessment, metrics);

            // Then
            assertNotNull(recommendations);
        }
    }

    @Nested
    @DisplayName("Improvement Plan Tests")
    class ImprovementPlanTests {

        @Test
        @DisplayName("Should generate improvement plan for low score merchants")
        void shouldGenerateImprovementPlanForLowScore() {
            // Given
            CreditAssessment assessment = createSampleAssessment();
            assessment.setCreditScore(65); // Below 80
            FinancialMetrics metrics = createSampleMetrics();
            
            when(llmClient.generateCompletion(anyString(), anyString()))
                .thenReturn("30-60-90 day improvement plan");

            // When
            String plan = service.generateImprovementPlan(assessment, metrics);

            // Then
            assertNotNull(plan);
            assertEquals("30-60-90 day improvement plan", plan);
        }

        @Test
        @DisplayName("Should return congratulations for excellent score")
        void shouldReturnCongratulationsForExcellentScore() {
            // Given
            CreditAssessment assessment = createSampleAssessment();
            assessment.setCreditScore(85); // Above 80
            assessment.setIsEligible(true);
            FinancialMetrics metrics = createSampleMetrics();

            // When
            String plan = service.generateImprovementPlan(assessment, metrics);

            // Then
            assertTrue(plan.contains("Congratulations") || plan.contains("excellent"));
        }
    }

    @Nested
    @DisplayName("Comparative Analysis Tests")
    class ComparativeAnalysisTests {

        @Test
        @DisplayName("Should generate industry comparison")
        void shouldGenerateIndustryComparison() {
            // Given
            CreditAssessment assessment = createSampleAssessment();
            
            when(llmClient.generateCompletion(argThat(prompt -> 
                prompt.contains("RETAIL")), anyString()))
                .thenReturn("Comparison with retail industry average");

            // When
            String analysis = service.generateComparativeAnalysis(assessment, "RETAIL");

            // Then
            assertNotNull(analysis);
            assertEquals("Comparison with retail industry average", analysis);
        }

        @Test
        @DisplayName("Should return empty when feature is disabled")
        void shouldReturnEmptyWhenFeatureDisabled() {
            // Given
            llmConfig.getFeatures().setComparativeAnalysis(false);
            service = new CreditInsightsService(llmClient, llmConfig);
            
            CreditAssessment assessment = createSampleAssessment();

            // When
            String analysis = service.generateComparativeAnalysis(assessment, "RETAIL");

            // Then
            assertEquals("", analysis);
        }
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private LlmConfig createDefaultConfig() {
        LlmConfig config = new LlmConfig();
        config.setEnabled(true);
        config.setProvider(LlmConfig.Provider.OPENAI);
        
        LlmConfig.Features features = new LlmConfig.Features();
        features.setCreditInsights(true);
        features.setRiskNarrative(true);
        features.setRecommendations(true);
        features.setChatInterface(true);
        features.setAnomalyExplanation(true);
        features.setComparativeAnalysis(true);
        config.setFeatures(features);
        
        return config;
    }

    private CreditAssessment createSampleAssessment() {
        List<String> warnings = new ArrayList<>();
        warnings.add("High customer concentration");
        
        List<String> strengths = new ArrayList<>();
        strengths.add("Consistent business");

        return CreditAssessment.builder()
            .merchantId("MERCHANT_001")
            .assessmentDate(LocalDateTime.now())
            .creditScore(75)
            .riskCategory(CreditAssessment.RiskCategory.MEDIUM)
            .isEligible(true)
            .eligibleLoanAmount(new BigDecimal("150000"))
            .maxTenureDays(90)
            .recommendedInterestRate(new BigDecimal("24"))
            .last3MonthsVolume(new BigDecimal("450000"))
            .last6MonthsVolume(new BigDecimal("900000"))
            .averageMonthlyVolume(new BigDecimal("150000"))
            .averageTransactionValue(new BigDecimal("5000"))
            .transactionCount(200)
            .uniqueCustomerCount(50)
            .consistencyScore(new BigDecimal("72"))
            .growthRate(new BigDecimal("15"))
            .bounceRate(new BigDecimal("7"))
            .customerConcentration(new BigDecimal("45"))
            .volumeScore(new BigDecimal("60"))
            .growthScore(new BigDecimal("70"))
            .bounceRateScore(new BigDecimal("70"))
            .concentrationScore(new BigDecimal("70"))
            .warnings(warnings)
            .strengths(strengths)
            .build();
    }

    private CreditAssessment createIneligibleAssessment() {
        CreditAssessment assessment = createSampleAssessment();
        assessment.setIsEligible(false);
        assessment.setIneligibilityReason("Volume too low");
        assessment.setCreditScore(45);
        assessment.setRiskCategory(CreditAssessment.RiskCategory.HIGH);
        return assessment;
    }

    private FinancialMetrics createSampleMetrics() {
        List<FinancialMetrics.MonthlyVolume> monthlyVolumes = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            monthlyVolumes.add(FinancialMetrics.MonthlyVolume.builder()
                .month("2024-0" + i)
                .volume(new BigDecimal("150000"))
                .transactionCount(30)
                .uniqueCustomers(20)
                .build());
        }

        return FinancialMetrics.builder()
            .last3MonthsVolume(new BigDecimal("450000"))
            .last6MonthsVolume(new BigDecimal("900000"))
            .averageMonthlyVolume(new BigDecimal("150000"))
            .averageTransactionValue(new BigDecimal("5000"))
            .totalTransactionCount(200)
            .successfulTransactionCount(186)
            .failedTransactionCount(14)
            .uniqueCustomerCount(50)
            .customerConcentration(new BigDecimal("45"))
            .consistencyScore(new BigDecimal("72"))
            .growthRate(new BigDecimal("15"))
            .bounceRate(new BigDecimal("7"))
            .monthlyVolumes(monthlyVolumes)
            .isSeasonalBusiness(false)
            .coefficientOfVariation(new BigDecimal("0.25"))
            .build();
    }
}
