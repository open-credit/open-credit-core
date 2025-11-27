package com.lumexpay.vortexa.credit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lumexpay.vortexa.credit.client.UpiPlatformClient;
import com.lumexpay.vortexa.credit.config.LlmConfig;
import com.lumexpay.vortexa.credit.dto.CreditAssessmentResponse;
import com.lumexpay.vortexa.credit.dto.EligibilityResponse;
import com.lumexpay.vortexa.credit.dto.FinancialMetrics;
import com.lumexpay.vortexa.credit.dto.UpiTransaction;
import com.lumexpay.vortexa.credit.model.CreditAssessment;
import com.lumexpay.vortexa.credit.repository.CreditAssessmentRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Main service orchestrating the credit assessment process.
 * This is the primary entry point for all credit assessment operations.
 * Now enhanced with AI-powered insights via FinGPT integration.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CreditAssessmentService {

    private final UpiPlatformClient upiPlatformClient;
    private final MetricsCalculationService metricsCalculationService;
    private final CreditScoringService creditScoringService;
    private final PdfReportService pdfReportService;
    private final CreditAssessmentRepository repository;
    private final CreditInsightsService insightsService;
    private final LlmConfig llmConfig;

    /**
     * Perform a complete credit assessment for a merchant.
     *
     * @param merchantId Merchant identifier
     * @return Credit assessment response
     */
    @Transactional
    public CreditAssessmentResponse assess(String merchantId) {
        log.info("Starting credit assessment for merchant: {}", merchantId);
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Fetch UPI transactions (last 12 months)
            log.debug("Fetching UPI transactions for merchant: {}", merchantId);
            List<UpiTransaction> transactions = upiPlatformClient.getTransactionsForLastMonths(merchantId, 12);
            log.info("Fetched {} transactions for merchant: {}", transactions.size(), merchantId);

            // Step 2: Calculate financial metrics
            log.debug("Calculating financial metrics");
            FinancialMetrics metrics = metricsCalculationService.calculateMetrics(transactions);

            // Step 3: Calculate credit score and eligibility
            log.debug("Calculating credit scores");
            CreditAssessment assessment = creditScoringService.calculateScores(metrics, merchantId);

            // Step 4: Generate PDF report
            log.debug("Generating PDF report");
            try {
                String reportUrl = pdfReportService.generateReport(assessment);
                assessment.setReportUrl(reportUrl);
            } catch (IOException e) {
                log.error("Failed to generate PDF report for merchant: {}", merchantId, e);
                // Continue without report - not critical
            }

            // Step 5: Save assessment
            assessment = repository.save(assessment);
            log.info("Credit assessment completed for merchant: {} with score: {} in {}ms",
                merchantId, assessment.getCreditScore(), System.currentTimeMillis() - startTime);

            return CreditAssessmentResponse.fromEntity(assessment);

        } catch (Exception e) {
            log.error("Credit assessment failed for merchant: {}", merchantId, e);
            throw new RuntimeException("Credit assessment failed: " + e.getMessage(), e);
        }
    }

    /**
     * Re-assess a merchant's credit (manual trigger).
     *
     * @param merchantId Merchant identifier
     * @return Updated credit assessment
     */
    @Transactional
    public CreditAssessmentResponse reassess(String merchantId) {
        log.info("Re-assessing credit for merchant: {}", merchantId);
        return assess(merchantId);
    }

    /**
     * Get the latest credit assessment for a merchant.
     *
     * @param merchantId Merchant identifier
     * @return Credit assessment response or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<CreditAssessmentResponse> getLatestAssessment(String merchantId) {
        log.debug("Fetching latest assessment for merchant: {}", merchantId);
        return repository.findTopByMerchantIdOrderByAssessmentDateDesc(merchantId)
            .map(CreditAssessmentResponse::fromEntity);
    }

    /**
     * Get assessment by ID.
     *
     * @param assessmentId Assessment UUID
     * @return Credit assessment response or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<CreditAssessmentResponse> getAssessmentById(UUID assessmentId) {
        log.debug("Fetching assessment by ID: {}", assessmentId);
        return repository.findById(assessmentId)
            .map(CreditAssessmentResponse::fromEntity);
    }

    /**
     * Get assessment history for a merchant.
     *
     * @param merchantId Merchant identifier
     * @return List of all assessments
     */
    @Transactional(readOnly = true)
    public List<CreditAssessmentResponse> getAssessmentHistory(String merchantId) {
        log.debug("Fetching assessment history for merchant: {}", merchantId);
        return repository.findByMerchantIdOrderByAssessmentDateDesc(merchantId)
            .stream()
            .map(CreditAssessmentResponse::fromEntity)
            .toList();
    }

    /**
     * Get eligibility summary for a merchant.
     *
     * @param merchantId Merchant identifier
     * @return Eligibility response
     */
    @Transactional(readOnly = true)
    public Optional<EligibilityResponse> getEligibility(String merchantId) {
        log.debug("Fetching eligibility for merchant: {}", merchantId);

        return repository.findTopByMerchantIdOrderByAssessmentDateDesc(merchantId)
            .map(this::buildEligibilityResponse);
    }

    /**
     * Build eligibility response from assessment.
     */
    private EligibilityResponse buildEligibilityResponse(CreditAssessment assessment) {
        EligibilityResponse.LoanRecommendations recommendations = null;

        if (assessment.getIsEligible()) {
            // Calculate recommended EMI (assuming full loan amount and max tenure)
            BigDecimal monthlyRate = assessment.getRecommendedInterestRate()
                .divide(new BigDecimal("1200"), 6, RoundingMode.HALF_UP);
            int tenureMonths = assessment.getMaxTenureDays() / 30;

            BigDecimal recommendedEmi = calculateEMI(
                assessment.getEligibleLoanAmount(), monthlyRate, tenureMonths);

            // Max monthly repayment (20% of average monthly volume)
            BigDecimal maxMonthlyRepayment = assessment.getAverageMonthlyVolume()
                .multiply(new BigDecimal("0.20"))
                .setScale(0, RoundingMode.HALF_UP);

            String comments = generateRecommendationComments(assessment);

            recommendations = EligibilityResponse.LoanRecommendations.builder()
                .recommendedEmi(recommendedEmi)
                .maxMonthlyRepayment(maxMonthlyRepayment)
                .suggestedLoanTenureDays(assessment.getMaxTenureDays())
                .comments(comments)
                .build();
        }

        return EligibilityResponse.builder()
            .merchantId(assessment.getMerchantId())
            .isEligible(assessment.getIsEligible())
            .creditScore(assessment.getCreditScore())
            .riskCategory(assessment.getRiskCategory().name())
            .eligibleLoanAmount(assessment.getEligibleLoanAmount())
            .eligibleLoanAmountFormatted(formatAmount(assessment.getEligibleLoanAmount()))
            .maxTenureDays(assessment.getMaxTenureDays())
            .maxTenureMonths(assessment.getMaxTenureDays() / 30)
            .recommendedInterestRate(assessment.getRecommendedInterestRate())
            .ineligibilityReason(assessment.getIneligibilityReason())
            .assessmentDate(assessment.getAssessmentDate())
            .recommendations(recommendations)
            .build();
    }

    /**
     * Calculate EMI using standard formula.
     */
    private BigDecimal calculateEMI(BigDecimal principal, BigDecimal monthlyRate, int tenureMonths) {
        if (tenureMonths == 0 || monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(new BigDecimal(Math.max(tenureMonths, 1)), 0, RoundingMode.HALF_UP);
        }

        // EMI = P * r * (1+r)^n / ((1+r)^n - 1)
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal onePlusRPowN = onePlusR.pow(tenureMonths);

        BigDecimal numerator = principal.multiply(monthlyRate).multiply(onePlusRPowN);
        BigDecimal denominator = onePlusRPowN.subtract(BigDecimal.ONE);

        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(new BigDecimal(tenureMonths), 0, RoundingMode.HALF_UP);
        }

        return numerator.divide(denominator, 0, RoundingMode.HALF_UP);
    }

    /**
     * Generate recommendation comments based on assessment.
     */
    private String generateRecommendationComments(CreditAssessment assessment) {
        StringBuilder comments = new StringBuilder();

        switch (assessment.getRiskCategory()) {
            case LOW -> comments.append("Excellent credit profile. Eligible for premium lending products. ");
            case MEDIUM -> comments.append("Good credit profile. Standard lending terms apply. ");
            case HIGH -> comments.append("Higher risk profile. Conservative lending terms recommended. ");
        }

        if (assessment.getConsistencyScore().compareTo(new BigDecimal("80")) >= 0) {
            comments.append("Highly consistent business performance noted. ");
        }

        if (assessment.getGrowthRate().compareTo(new BigDecimal("20")) >= 0) {
            comments.append("Strong growth trajectory observed. ");
        }

        return comments.toString().trim();
    }

    /**
     * Format amount for display.
     */
    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "₹0";

        BigDecimal lakhs = amount.divide(new BigDecimal("100000"), 2, RoundingMode.HALF_UP);
        if (lakhs.compareTo(BigDecimal.ONE) >= 0) {
            return "₹" + lakhs.setScale(2, RoundingMode.HALF_UP) + " Lakhs";
        }

        return "₹" + amount.setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Check if merchant has a recent assessment (within last N days).
     *
     * @param merchantId Merchant identifier
     * @param days       Number of days to check
     * @return true if recent assessment exists
     */
    @Transactional(readOnly = true)
    public boolean hasRecentAssessment(String merchantId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return repository.hasRecentAssessment(merchantId, since);
    }

    /**
     * Get all eligible merchants.
     *
     * @return List of eligible merchant assessments
     */
    @Transactional(readOnly = true)
    public List<CreditAssessmentResponse> getEligibleMerchants() {
        return repository.findByIsEligibleTrue()
            .stream()
            .map(CreditAssessmentResponse::fromEntity)
            .toList();
    }

    /**
     * Get merchants by risk category.
     *
     * @param riskCategory Risk category filter
     * @return List of merchant assessments
     */
    @Transactional(readOnly = true)
    public List<CreditAssessmentResponse> getMerchantsByRiskCategory(CreditAssessment.RiskCategory riskCategory) {
        return repository.findByRiskCategory(riskCategory)
            .stream()
            .map(CreditAssessmentResponse::fromEntity)
            .toList();
    }

    /**
     * Get PDF report file path.
     *
     * @param merchantId Merchant identifier
     * @return File path or null if not found
     */
    @Transactional(readOnly = true)
    public String getReportFilePath(String merchantId) {
        return repository.findTopByMerchantIdOrderByAssessmentDateDesc(merchantId)
            .map(assessment -> {
                if (assessment.getReportFileName() != null) {
                    return pdfReportService.getReportFilePath(assessment.getReportFileName());
                }
                return null;
            })
            .orElse(null);
    }
}
