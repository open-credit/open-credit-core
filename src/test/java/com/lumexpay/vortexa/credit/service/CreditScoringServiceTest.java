package com.lumexpay.vortexa.credit.service;

import com.lumexpay.vortexa.credit.config.CreditAssessmentConfig;
import com.lumexpay.vortexa.credit.dto.FinancialMetrics;
import com.lumexpay.vortexa.credit.model.CreditAssessment;
import com.lumexpay.vortexa.credit.rules.RuleEngine;
import com.lumexpay.vortexa.credit.rules.RuleEngine.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CreditScoringService.
 */
@DisplayName("Credit Scoring Service Tests")
@ExtendWith(MockitoExtension.class)
class CreditScoringServiceTest {

    private CreditScoringService service;
    private CreditAssessmentConfig config;
    
    @Mock
    private RuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        config = createDefaultConfig();
        
        // Setup default mock behavior for RuleEngine
        setupDefaultRuleEngineMocks();
        
        service = new CreditScoringService(config, ruleEngine);
    }
    
    private void setupDefaultRuleEngineMocks() {
        // Mock getRulesVersion
        when(ruleEngine.getRulesVersion()).thenReturn("1.0.0");
        
        // Mock checkFraudIndicators - return empty list by default
        when(ruleEngine.checkFraudIndicators(any())).thenReturn(new ArrayList<>());
        
        // Mock calculateScore - return default scoring result
        ScoringResult defaultScoringResult = ScoringResult.builder()
            .creditScore(75)
            .riskCategory("MEDIUM")
            .componentScores(createDefaultComponentScores())
            .warnings(new ArrayList<>())
            .strengths(new ArrayList<>())
            .rulesVersion("1.0.0")
            .build();
        when(ruleEngine.calculateScore(any())).thenReturn(defaultScoringResult);
        
        // Mock checkEligibility - return eligible by default
        EligibilityResult defaultEligibilityResult = EligibilityResult.builder()
            .eligible(true)
            .rulesChecked(5)
            .rulesPassed(5)
            .ruleResults(new ArrayList<>())
            .build();
        when(ruleEngine.checkEligibility(any(), anyInt())).thenReturn(defaultEligibilityResult);
        
        // Mock calculateLoanParameters
        LoanParametersResult defaultLoanParams = LoanParametersResult.builder()
            .eligibleAmount(new BigDecimal("50000"))
            .maxTenureDays(180)
            .interestRateAnnual(new BigDecimal("24"))
            .volumeMultiplier(new BigDecimal("0.25"))
            .build();
        when(ruleEngine.calculateLoanParameters(anyString(), any(), any())).thenReturn(defaultLoanParams);
    }
    
    private Map<String, ComponentScore> createDefaultComponentScores() {
        Map<String, ComponentScore> scores = new HashMap<>();
        scores.put("volume", ComponentScore.builder().score(new BigDecimal("60")).weight(new BigDecimal("0.30")).build());
        scores.put("consistency", ComponentScore.builder().score(new BigDecimal("70")).weight(new BigDecimal("0.25")).build());
        scores.put("growth", ComponentScore.builder().score(new BigDecimal("80")).weight(new BigDecimal("0.15")).build());
        scores.put("bounce_rate", ComponentScore.builder().score(new BigDecimal("75")).weight(new BigDecimal("0.15")).build());
        scores.put("concentration", ComponentScore.builder().score(new BigDecimal("65")).weight(new BigDecimal("0.15")).build());
        return scores;
    }

    @Nested
    @DisplayName("Volume Score Tests")
    class VolumeScoreTests {

        @Test
        @DisplayName("Should return 100 for excellent volume (≥5 lakhs)")
        void shouldReturn100ForExcellentVolume() {
            BigDecimal volume = new BigDecimal("600000"); // 6 lakhs
            BigDecimal score = service.calculateVolumeScore(volume);
            assertEquals(0, score.compareTo(new BigDecimal("100")));
        }

        @Test
        @DisplayName("Should return 80 for good volume (2-5 lakhs)")
        void shouldReturn80ForGoodVolume() {
            BigDecimal volume = new BigDecimal("300000"); // 3 lakhs
            BigDecimal score = service.calculateVolumeScore(volume);
            assertEquals(0, score.compareTo(new BigDecimal("80")));
        }

        @Test
        @DisplayName("Should return 60 for moderate volume (1-2 lakhs)")
        void shouldReturn60ForModerateVolume() {
            BigDecimal volume = new BigDecimal("150000"); // 1.5 lakhs
            BigDecimal score = service.calculateVolumeScore(volume);
            assertEquals(0, score.compareTo(new BigDecimal("60")));
        }

        @Test
        @DisplayName("Should return 40 for low volume (50k-1 lakh)")
        void shouldReturn40ForLowVolume() {
            BigDecimal volume = new BigDecimal("75000"); // 75k
            BigDecimal score = service.calculateVolumeScore(volume);
            assertEquals(0, score.compareTo(new BigDecimal("40")));
        }

        @Test
        @DisplayName("Should return 20 for very low volume (<50k)")
        void shouldReturn20ForVeryLowVolume() {
            BigDecimal volume = new BigDecimal("30000"); // 30k
            BigDecimal score = service.calculateVolumeScore(volume);
            assertEquals(0, score.compareTo(new BigDecimal("20")));
        }

        @Test
        @DisplayName("Should handle null volume gracefully")
        void shouldHandleNullVolume() {
            BigDecimal score = service.calculateVolumeScore(null);
            assertEquals(0, score.compareTo(new BigDecimal("20")));
        }
    }

    @Nested
    @DisplayName("Growth Score Tests")
    class GrowthScoreTests {

        @Test
        @DisplayName("Should return 100 for high growth (>30%)")
        void shouldReturn100ForHighGrowth() {
            BigDecimal growthRate = new BigDecimal("45"); // 45%
            BigDecimal score = service.calculateGrowthScore(growthRate);
            assertEquals(0, score.compareTo(new BigDecimal("100")));
        }

        @Test
        @DisplayName("Should return 70 for positive growth (0-30%)")
        void shouldReturn70ForPositiveGrowth() {
            BigDecimal growthRate = new BigDecimal("15"); // 15%
            BigDecimal score = service.calculateGrowthScore(growthRate);
            assertEquals(0, score.compareTo(new BigDecimal("70")));
        }

        @Test
        @DisplayName("Should return 40 for slight decline (-10% to 0%)")
        void shouldReturn40ForSlightDecline() {
            BigDecimal growthRate = new BigDecimal("-5"); // -5%
            BigDecimal score = service.calculateGrowthScore(growthRate);
            assertEquals(0, score.compareTo(new BigDecimal("40")));
        }

        @Test
        @DisplayName("Should return 20 for significant decline (< -10%)")
        void shouldReturn20ForSignificantDecline() {
            BigDecimal growthRate = new BigDecimal("-20"); // -20%
            BigDecimal score = service.calculateGrowthScore(growthRate);
            assertEquals(0, score.compareTo(new BigDecimal("20")));
        }

        @Test
        @DisplayName("Should handle null growth rate")
        void shouldHandleNullGrowthRate() {
            BigDecimal score = service.calculateGrowthScore(null);
            assertEquals(0, score.compareTo(new BigDecimal("50")));
        }
    }

    @Nested
    @DisplayName("Bounce Rate Score Tests")
    class BounceRateScoreTests {

        @Test
        @DisplayName("Should return 100 for excellent bounce rate (<5%)")
        void shouldReturn100ForExcellentBounceRate() {
            BigDecimal bounceRate = new BigDecimal("3"); // 3%
            BigDecimal score = service.calculateBounceRateScore(bounceRate);
            assertEquals(0, score.compareTo(new BigDecimal("100")));
        }

        @Test
        @DisplayName("Should return 70 for good bounce rate (5-10%)")
        void shouldReturn70ForGoodBounceRate() {
            BigDecimal bounceRate = new BigDecimal("7"); // 7%
            BigDecimal score = service.calculateBounceRateScore(bounceRate);
            assertEquals(0, score.compareTo(new BigDecimal("70")));
        }

        @Test
        @DisplayName("Should return 40 for moderate bounce rate (10-15%)")
        void shouldReturn40ForModerateBounceRate() {
            BigDecimal bounceRate = new BigDecimal("12"); // 12%
            BigDecimal score = service.calculateBounceRateScore(bounceRate);
            assertEquals(0, score.compareTo(new BigDecimal("40")));
        }

        @Test
        @DisplayName("Should return 20 for high bounce rate (>15%)")
        void shouldReturn20ForHighBounceRate() {
            BigDecimal bounceRate = new BigDecimal("20"); // 20%
            BigDecimal score = service.calculateBounceRateScore(bounceRate);
            assertEquals(0, score.compareTo(new BigDecimal("20")));
        }
    }

    @Nested
    @DisplayName("Customer Concentration Score Tests")
    class ConcentrationScoreTests {

        @Test
        @DisplayName("Should return 100 for low concentration (<30%)")
        void shouldReturn100ForLowConcentration() {
            BigDecimal concentration = new BigDecimal("25"); // 25%
            BigDecimal score = service.calculateConcentrationScore(concentration);
            assertEquals(0, score.compareTo(new BigDecimal("100")));
        }

        @Test
        @DisplayName("Should return 70 for moderate concentration (30-60%)")
        void shouldReturn70ForModerateConcentration() {
            BigDecimal concentration = new BigDecimal("45"); // 45%
            BigDecimal score = service.calculateConcentrationScore(concentration);
            assertEquals(0, score.compareTo(new BigDecimal("70")));
        }

        @Test
        @DisplayName("Should return 40 for high concentration (>60%)")
        void shouldReturn40ForHighConcentration() {
            BigDecimal concentration = new BigDecimal("75"); // 75%
            BigDecimal score = service.calculateConcentrationScore(concentration);
            assertEquals(0, score.compareTo(new BigDecimal("40")));
        }
    }

    @Nested
    @DisplayName("Weighted Score Calculation Tests")
    class WeightedScoreTests {

        @Test
        @DisplayName("Should calculate correct weighted score")
        void shouldCalculateWeightedScore() {
            // Given: All perfect scores
            BigDecimal volumeScore = new BigDecimal("100");
            BigDecimal consistencyScore = new BigDecimal("100");
            BigDecimal growthScore = new BigDecimal("100");
            BigDecimal bounceRateScore = new BigDecimal("100");
            BigDecimal concentrationScore = new BigDecimal("100");

            // When
            BigDecimal weightedScore = service.calculateWeightedScore(
                volumeScore, consistencyScore, growthScore, bounceRateScore, concentrationScore);

            // Then: Should be 100 (all perfect)
            assertEquals(0, weightedScore.compareTo(new BigDecimal("100")));
        }

        @Test
        @DisplayName("Should apply correct weights")
        void shouldApplyCorrectWeights() {
            // Given: Mixed scores
            // Volume: 100 * 0.30 = 30
            // Consistency: 80 * 0.25 = 20
            // Growth: 70 * 0.15 = 10.5
            // BounceRate: 70 * 0.15 = 10.5
            // Concentration: 100 * 0.15 = 15
            // Total = 86

            BigDecimal weightedScore = service.calculateWeightedScore(
                new BigDecimal("100"),  // volume
                new BigDecimal("80"),   // consistency
                new BigDecimal("70"),   // growth
                new BigDecimal("70"),   // bounce rate
                new BigDecimal("100")   // concentration
            );

            assertEquals(0, weightedScore.compareTo(new BigDecimal("86")));
        }
    }

    @Nested
    @DisplayName("Risk Category Tests")
    class RiskCategoryTests {

        @Test
        @DisplayName("Should return LOW risk for score >= 80")
        void shouldReturnLowRiskForHighScore() {
            assertEquals(CreditAssessment.RiskCategory.LOW, service.determineRiskCategory(85));
            assertEquals(CreditAssessment.RiskCategory.LOW, service.determineRiskCategory(80));
            assertEquals(CreditAssessment.RiskCategory.LOW, service.determineRiskCategory(100));
        }

        @Test
        @DisplayName("Should return MEDIUM risk for score 60-79")
        void shouldReturnMediumRiskForMidScore() {
            assertEquals(CreditAssessment.RiskCategory.MEDIUM, service.determineRiskCategory(79));
            assertEquals(CreditAssessment.RiskCategory.MEDIUM, service.determineRiskCategory(60));
            assertEquals(CreditAssessment.RiskCategory.MEDIUM, service.determineRiskCategory(70));
        }

        @Test
        @DisplayName("Should return HIGH risk for score < 60")
        void shouldReturnHighRiskForLowScore() {
            assertEquals(CreditAssessment.RiskCategory.HIGH, service.determineRiskCategory(59));
            assertEquals(CreditAssessment.RiskCategory.HIGH, service.determineRiskCategory(40));
            assertEquals(CreditAssessment.RiskCategory.HIGH, service.determineRiskCategory(0));
        }
    }

    @Nested
    @DisplayName("Loan Amount Calculation Tests")
    class LoanAmountTests {

        @Test
        @DisplayName("Should calculate 30% for LOW risk")
        void shouldCalculate30PercentForLowRisk() {
            BigDecimal avgMonthlyVolume = new BigDecimal("100000");
            BigDecimal loanAmount = service.calculateEligibleLoanAmount(
               CreditAssessment.RiskCategory.LOW, avgMonthlyVolume );
            
            // 30% of 100000 = 30000
            assertEquals(0, loanAmount.compareTo(new BigDecimal("30000")));
        }

        @Test
        @DisplayName("Should calculate 25% for MEDIUM risk")
        void shouldCalculate25PercentForMediumRisk() {
            BigDecimal avgMonthlyVolume = new BigDecimal("100000");
            BigDecimal loanAmount = service.calculateEligibleLoanAmount(
                 CreditAssessment.RiskCategory.MEDIUM, avgMonthlyVolume);
            
            // 25% of 100000 = 25000
            assertEquals(0, loanAmount.compareTo(new BigDecimal("25000")));
        }

        @Test
        @DisplayName("Should calculate 15% for HIGH risk")
        void shouldCalculate15PercentForHighRisk() {
            BigDecimal avgMonthlyVolume = new BigDecimal("100000");
            BigDecimal loanAmount = service.calculateEligibleLoanAmount(
               CreditAssessment.RiskCategory.HIGH, avgMonthlyVolume );
            
            // 15% of 100000 = 15000
            assertEquals(0, loanAmount.compareTo(new BigDecimal("15000")));
        }

        @Test
        @DisplayName("Should apply minimum loan amount cap")
        void shouldApplyMinimumCap() {
            BigDecimal avgMonthlyVolume = new BigDecimal("10000"); // Very low
            BigDecimal loanAmount = service.calculateEligibleLoanAmount(
                CreditAssessment.RiskCategory.LOW, avgMonthlyVolume );
            
            // Should be capped at minimum 10000
            assertTrue(loanAmount.compareTo(new BigDecimal("10000")) >= 0);
        }

        @Test
        @DisplayName("Should apply maximum loan amount cap")
        void shouldApplyMaximumCap() {
            BigDecimal avgMonthlyVolume = new BigDecimal("50000000"); // Very high
            BigDecimal loanAmount = service.calculateEligibleLoanAmount(
                CreditAssessment.RiskCategory.LOW, avgMonthlyVolume );
            
            // Should be capped at maximum 5000000 (50 lakhs)
            assertTrue(loanAmount.compareTo(new BigDecimal("5000000")) <= 0);
        }
    }

    @Nested
    @DisplayName("Tenure Calculation Tests")
    class TenureTests {

        @Test
        @DisplayName("Should return 365 days for LOW risk with high consistency")
        void shouldReturn365ForLowRiskConsistent() {
            int tenure = service.calculateMaxTenure(
                CreditAssessment.RiskCategory.LOW, new BigDecimal("85"));
            assertEquals(365, tenure);
        }

        @Test
        @DisplayName("Should return 180 days for LOW risk with low consistency")
        void shouldReturn180ForLowRiskInconsistent() {
            int tenure = service.calculateMaxTenure(
                CreditAssessment.RiskCategory.LOW, new BigDecimal("60"));
            assertEquals(180, tenure);
        }

        @Test
        @DisplayName("Should return 90 days for MEDIUM risk")
        void shouldReturn90ForMediumRisk() {
            int tenure = service.calculateMaxTenure(
                CreditAssessment.RiskCategory.MEDIUM, new BigDecimal("70"));
            assertEquals(90, tenure);
        }

        @Test
        @DisplayName("Should return 30 days for HIGH risk")
        void shouldReturn30ForHighRisk() {
            int tenure = service.calculateMaxTenure(
                CreditAssessment.RiskCategory.HIGH, new BigDecimal("50"));
            assertEquals(30, tenure);
        }
    }

    @Nested
    @DisplayName("Interest Rate Tests")
    class InterestRateTests {

        @Test
        @DisplayName("Should return 18% for LOW risk")
        void shouldReturn18ForLowRisk() {
            BigDecimal rate = service.determineInterestRate(CreditAssessment.RiskCategory.LOW);
            assertEquals(0, rate.compareTo(new BigDecimal("18")));
        }

        @Test
        @DisplayName("Should return 24% for MEDIUM risk")
        void shouldReturn24ForMediumRisk() {
            BigDecimal rate = service.determineInterestRate(CreditAssessment.RiskCategory.MEDIUM);
            assertEquals(0, rate.compareTo(new BigDecimal("24")));
        }

        @Test
        @DisplayName("Should return 30% for HIGH risk")
        void shouldReturn30ForHighRisk() {
            BigDecimal rate = service.determineInterestRate(CreditAssessment.RiskCategory.HIGH);
            assertEquals(0, rate.compareTo(new BigDecimal("30")));
        }
    }

    @Nested
    @DisplayName("Full Assessment Integration Tests")
    class FullAssessmentTests {

        @Test
        @DisplayName("Should produce eligible assessment for good metrics")
        void shouldProduceEligibleAssessmentForGoodMetrics() {
            // Given: Good financial metrics
            FinancialMetrics metrics = createGoodMetrics();

            // When
            CreditAssessment assessment = service.calculateScores(metrics, "TEST_MERCHANT");

            // Then
            assertTrue(assessment.getIsEligible());
            assertTrue(assessment.getCreditScore() >= 60);
            assertNotNull(assessment.getEligibleLoanAmount());
            assertTrue(assessment.getEligibleLoanAmount().compareTo(BigDecimal.ZERO) > 0);
        }

        @Test
        @DisplayName("Should produce ineligible assessment for poor metrics")
        void shouldProduceIneligibleAssessmentForPoorMetrics() {
            // Given: Poor financial metrics (below thresholds)
            FinancialMetrics metrics = createPoorMetrics();

            // When
            CreditAssessment assessment = service.calculateScores(metrics, "TEST_MERCHANT");

            // Then
            assertFalse(assessment.getIsEligible());
            assertNotNull(assessment.getIneligibilityReason());
        }

        @Test
        @DisplayName("Should generate warnings for risky indicators")
        void shouldGenerateWarningsForRiskyIndicators() {
            // Given: Metrics with high bounce rate
            FinancialMetrics metrics = createGoodMetrics();
            metrics.setBounceRate(new BigDecimal("15")); // High bounce

            // When
            CreditAssessment assessment = service.calculateScores(metrics, "TEST_MERCHANT");

            // Then
            assertFalse(assessment.getWarnings().isEmpty());
            assertTrue(assessment.getWarnings().stream()
                .anyMatch(w -> w.toLowerCase().contains("bounce")));
        }

        @Test
        @DisplayName("Should generate strengths for positive indicators")
        void shouldGenerateStrengthsForPositiveIndicators() {
            // Given: Excellent metrics
            FinancialMetrics metrics = createExcellentMetrics();

            // When
            CreditAssessment assessment = service.calculateScores(metrics, "TEST_MERCHANT");

            // Then
            assertFalse(assessment.getStrengths().isEmpty());
        }
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private CreditAssessmentConfig createDefaultConfig() {
        CreditAssessmentConfig config = new CreditAssessmentConfig();
        config.setMinMonthlyVolume(new BigDecimal("25000"));
        config.setMinTransactionCount(20);
        config.setMaxBounceRate(new BigDecimal("20"));
        config.setMinTenureMonths(3);

        CreditAssessmentConfig.Weights weights = new CreditAssessmentConfig.Weights();
        weights.setVolume(new BigDecimal("0.30"));
        weights.setConsistency(new BigDecimal("0.25"));
        weights.setGrowth(new BigDecimal("0.15"));
        weights.setBounceRate(new BigDecimal("0.15"));
        weights.setConcentration(new BigDecimal("0.15"));
        config.setWeights(weights);

        CreditAssessmentConfig.VolumeThresholds thresholds = new CreditAssessmentConfig.VolumeThresholds();
        thresholds.setExcellent(new BigDecimal("500000"));
        thresholds.setGood(new BigDecimal("200000"));
        thresholds.setAverage(new BigDecimal("100000"));
        thresholds.setBelowAverage(new BigDecimal("50000"));
        thresholds.setLow(new BigDecimal("25000"));
        config.setVolumeThresholds(thresholds);

        CreditAssessmentConfig.RiskThresholds riskThresholds = new CreditAssessmentConfig.RiskThresholds();
        riskThresholds.setLowRiskMin(80);
        riskThresholds.setMediumRiskMin(60);
        config.setRiskThresholds(riskThresholds);

        CreditAssessmentConfig.Eligibility eligibility = new CreditAssessmentConfig.Eligibility();
        eligibility.setLowRiskMultiplier(new BigDecimal("0.30"));
        eligibility.setMediumRiskMultiplier(new BigDecimal("0.25"));
        eligibility.setHighRiskMultiplier(new BigDecimal("0.15"));
        eligibility.setMinLoanAmount(new BigDecimal("10000"));
        eligibility.setMaxLoanAmount(new BigDecimal("5000000"));
        config.setEligibility(eligibility);

        CreditAssessmentConfig.Tenure tenure = new CreditAssessmentConfig.Tenure();
        tenure.setLowRiskConsistent(365);
        tenure.setLowRisk(180);
        tenure.setMediumRisk(90);
        tenure.setHighRisk(30);
        config.setTenure(tenure);

        CreditAssessmentConfig.InterestRates rates = new CreditAssessmentConfig.InterestRates();
        rates.setLowRisk(new BigDecimal("18"));
        rates.setMediumRisk(new BigDecimal("24"));
        rates.setHighRisk(new BigDecimal("30"));
        config.setInterestRates(rates);

        return config;
    }

    private FinancialMetrics createGoodMetrics() {
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
            .last12MonthsVolume(new BigDecimal("1800000"))
            .averageMonthlyVolume(new BigDecimal("150000"))
            .averageTransactionValue(new BigDecimal("5000"))
            .totalTransactionCount(200)
            .successfulTransactionCount(190)
            .failedTransactionCount(10)
            .uniqueCustomerCount(50)
            .customerConcentration(new BigDecimal("35"))
            .consistencyScore(new BigDecimal("75"))
            .growthRate(new BigDecimal("15"))
            .bounceRate(new BigDecimal("5"))
            .monthlyVolumes(monthlyVolumes)
            .isSeasonalBusiness(false)
            .hasSuddenVolumeSpike(false)
            .hasLowCustomerDiversity(false)
            .hasSinglePayerDominance(false)
            .build();
    }

    private FinancialMetrics createPoorMetrics() {
        return FinancialMetrics.builder()
            .last3MonthsVolume(new BigDecimal("30000"))
            .last6MonthsVolume(new BigDecimal("60000"))
            .last12MonthsVolume(new BigDecimal("120000"))
            .averageMonthlyVolume(new BigDecimal("10000")) // Below minimum
            .averageTransactionValue(new BigDecimal("500"))
            .totalTransactionCount(15) // Below minimum
            .successfulTransactionCount(10)
            .failedTransactionCount(5)
            .uniqueCustomerCount(3)
            .customerConcentration(new BigDecimal("80"))
            .consistencyScore(new BigDecimal("30"))
            .growthRate(new BigDecimal("-20"))
            .bounceRate(new BigDecimal("25")) // Above maximum
            .monthlyVolumes(new ArrayList<>())
            .isSeasonalBusiness(false)
            .hasSuddenVolumeSpike(false)
            .hasLowCustomerDiversity(true)
            .hasSinglePayerDominance(true)
            .build();
    }

    private FinancialMetrics createExcellentMetrics() {
        List<FinancialMetrics.MonthlyVolume> monthlyVolumes = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            monthlyVolumes.add(FinancialMetrics.MonthlyVolume.builder()
                .month(String.format("2024-%02d", i))
                .volume(new BigDecimal("600000"))
                .transactionCount(100)
                .uniqueCustomers(60)
                .build());
        }

        return FinancialMetrics.builder()
            .last3MonthsVolume(new BigDecimal("1800000"))
            .last6MonthsVolume(new BigDecimal("3600000"))
            .last12MonthsVolume(new BigDecimal("7200000"))
            .averageMonthlyVolume(new BigDecimal("600000"))
            .averageTransactionValue(new BigDecimal("6000"))
            .totalTransactionCount(1200)
            .successfulTransactionCount(1170)
            .failedTransactionCount(30)
            .uniqueCustomerCount(200)
            .customerConcentration(new BigDecimal("20"))
            .consistencyScore(new BigDecimal("90"))
            .growthRate(new BigDecimal("35"))
            .bounceRate(new BigDecimal("2.5"))
            .monthlyVolumes(monthlyVolumes)
            .isSeasonalBusiness(false)
            .hasSuddenVolumeSpike(false)
            .hasLowCustomerDiversity(false)
            .hasSinglePayerDominance(false)
            .build();
    }
}