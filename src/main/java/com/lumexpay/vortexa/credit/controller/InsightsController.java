package com.lumexpay.vortexa.credit.controller;

import com.lumexpay.vortexa.credit.client.UpiPlatformClient;
import com.lumexpay.vortexa.credit.dto.FinancialMetrics;
import com.lumexpay.vortexa.credit.dto.UpiTransaction;
import com.lumexpay.vortexa.credit.model.CreditAssessment;
import com.lumexpay.vortexa.credit.repository.CreditAssessmentRepository;
import com.lumexpay.vortexa.credit.service.CreditInsightsService;
import com.lumexpay.vortexa.credit.service.CreditInsightsService.CreditInsights;
import com.lumexpay.vortexa.credit.service.MetricsCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API controller for AI-powered credit insights.
 * Provides LLM-generated analysis, recommendations, and explanations.
 */
@RestController
@RequestMapping("/insights")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Insights", description = "AI-powered credit analysis and recommendations using FinGPT")
public class InsightsController {

    private final CreditInsightsService insightsService;
    private final CreditAssessmentRepository assessmentRepository;
    private final MetricsCalculationService metricsCalculationService;
    private final UpiPlatformClient upiPlatformClient;

    /**
     * Get comprehensive AI insights for a merchant.
     */
    @GetMapping("/{merchantId}")
    @Operation(
        summary = "Get AI insights",
        description = "Generate comprehensive AI-powered insights including credit narrative, " +
                      "risk analysis, recommendations, and improvement plan"
    )
    public ResponseEntity<?> getInsights(
            @Parameter(description = "Merchant ID") @PathVariable String merchantId) {
        
        log.info("Generating AI insights for merchant: {}", merchantId);
        
        Optional<CreditAssessment> assessmentOpt = assessmentRepository
            .findTopByMerchantIdOrderByAssessmentDateDesc(merchantId);
        
        if (assessmentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        CreditAssessment assessment = assessmentOpt.get();
        
        // Fetch transactions and calculate metrics for full context
        List<UpiTransaction> transactions = upiPlatformClient.getTransactionsForLastMonths(merchantId, 12);
        FinancialMetrics metrics = metricsCalculationService.calculateMetrics(transactions);
        
        CreditInsights insights = insightsService.generateInsights(assessment, metrics);
        
        return ResponseEntity.ok(insights);
    }

    /**
     * Get credit score explanation.
     */
    @GetMapping("/{merchantId}/narrative")
    @Operation(
        summary = "Get credit narrative",
        description = "Generate natural language explanation of the credit score"
    )
    public ResponseEntity<?> getCreditNarrative(@PathVariable String merchantId) {
        log.info("Generating credit narrative for merchant: {}", merchantId);
        
        Optional<CreditAssessment> assessmentOpt = assessmentRepository
            .findTopByMerchantIdOrderByAssessmentDateDesc(merchantId);
        
        if (assessmentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        CreditAssessment assessment = assessmentOpt.get();
        List<UpiTransaction> transactions = upiPlatformClient.getTransactionsForLastMonths(merchantId, 12);
        FinancialMetrics metrics = metricsCalculationService.calculateMetrics(transactions);
        
        String narrative = insightsService.generateCreditNarrative(assessment, metrics);
        
        return ResponseEntity.ok(Map.of(
            "merchantId", merchantId,
            "creditScore", assessment.getCreditScore(),
            "narrative", narrative
        ));
    }

    /**
     * Get risk analysis.
     */
    @GetMapping("/{merchantId}/risk-analysis")
    @Operation(
        summary = "Get risk analysis",
        description = "Generate detailed AI risk analysis for lender review"
    )
    public ResponseEntity<?> getRiskAnalysis(@PathVariable String merchantId) {
        log.info("Generating risk analysis for merchant: {}", merchantId);
        
        Optional<CreditAssessment> assessmentOpt = assessmentRepository
            .findTopByMerchantIdOrderByAssessmentDateDesc(merchantId);
        
        if (assessmentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        CreditAssessment assessment = assessmentOpt.get();
        List<UpiTransaction> transactions = upiPlatformClient.getTransactionsForLastMonths(merchantId, 12);
        FinancialMetrics metrics = metricsCalculationService.calculateMetrics(transactions);
        
        String riskAnalysis = insightsService.generateRiskAnalysis(assessment, metrics);
        
        return ResponseEntity.ok(Map.of(
            "merchantId", merchantId,
            "riskCategory", assessment.getRiskCategory().name(),
            "riskDescription", assessment.getRiskCategory().getDescription(),
            "analysis", riskAnalysis
        ));
    }

    /**
     * Get personalized recommendations.
     */
    @GetMapping("/{merchantId}/recommendations")
    @Operation(
        summary = "Get recommendations",
        description = "Generate personalized AI recommendations to improve credit profile"
    )
    public ResponseEntity<?> getRecommendations(@PathVariable String merchantId) {
        log.info("Generating recommendations for merchant: {}", merchantId);
        
        Optional<CreditAssessment> assessmentOpt = assessmentRepository
            .findTopByMerchantIdOrderByAssessmentDateDesc(merchantId);
        
        if (assessmentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        CreditAssessment assessment = assessmentOpt.get();
        List<UpiTransaction> transactions = upiPlatformClient.getTransactionsForLastMonths(merchantId, 12);
        FinancialMetrics metrics = metricsCalculationService.calculateMetrics(transactions);
        
        String recommendations = insightsService.generateRecommendations(assessment, metrics);
        
        return ResponseEntity.ok(Map.of(
            "merchantId", merchantId,
            "currentScore", assessment.getCreditScore(),
            "isEligible", assessment.getIsEligible(),
            "recommendations", recommendations
        ));
    }

    /**
     * Get improvement plan.
     */
    @GetMapping("/{merchantId}/improvement-plan")
    @Operation(
        summary = "Get improvement plan",
        description = "Generate 30-60-90 day improvement plan for the merchant"
    )
    public ResponseEntity<?> getImprovementPlan(@PathVariable String merchantId) {
        log.info("Generating improvement plan for merchant: {}", merchantId);
        
        Optional<CreditAssessment> assessmentOpt = assessmentRepository
            .findTopByMerchantIdOrderByAssessmentDateDesc(merchantId);
        
        if (assessmentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        CreditAssessment assessment = assessmentOpt.get();
        List<UpiTransaction> transactions = upiPlatformClient.getTransactionsForLastMonths(merchantId, 12);
        FinancialMetrics metrics = metricsCalculationService.calculateMetrics(transactions);
        
        String improvementPlan = insightsService.generateImprovementPlan(assessment, metrics);
        
        return ResponseEntity.ok(Map.of(
            "merchantId", merchantId,
            "currentScore", assessment.getCreditScore(),
            "targetScore", 80,
            "plan", improvementPlan
        ));
    }

    /**
     * Get comparative analysis.
     */
    @GetMapping("/{merchantId}/compare/{industry}")
    @Operation(
        summary = "Get comparative analysis",
        description = "Compare merchant against industry benchmarks"
    )
    public ResponseEntity<?> getComparativeAnalysis(
            @PathVariable String merchantId,
            @PathVariable String industry) {
        
        log.info("Generating comparative analysis for merchant: {} in industry: {}", merchantId, industry);
        
        Optional<CreditAssessment> assessmentOpt = assessmentRepository
            .findTopByMerchantIdOrderByAssessmentDateDesc(merchantId);
        
        if (assessmentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        CreditAssessment assessment = assessmentOpt.get();
        String analysis = insightsService.generateComparativeAnalysis(assessment, industry);
        
        return ResponseEntity.ok(Map.of(
            "merchantId", merchantId,
            "industry", industry,
            "creditScore", assessment.getCreditScore(),
            "comparativeAnalysis", analysis
        ));
    }

    /**
     * Explain an anomaly.
     */
    @PostMapping("/{merchantId}/explain-anomaly")
    @Operation(
        summary = "Explain anomaly",
        description = "Get AI explanation for a detected anomaly in transaction data"
    )
    public ResponseEntity<?> explainAnomaly(
            @PathVariable String merchantId,
            @RequestBody Map<String, String> request) {
        
        String anomalyType = request.get("anomalyType");
        if (anomalyType == null || anomalyType.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "anomalyType is required",
                "validTypes", List.of(
                    "SUDDEN_VOLUME_SPIKE",
                    "HIGH_CUSTOMER_CONCENTRATION",
                    "SEASONAL_PATTERN",
                    "DECLINING_VOLUME",
                    "HIGH_BOUNCE_RATE"
                )
            ));
        }
        
        log.info("Explaining anomaly {} for merchant: {}", anomalyType, merchantId);
        
        List<UpiTransaction> transactions = upiPlatformClient.getTransactionsForLastMonths(merchantId, 12);
        FinancialMetrics metrics = metricsCalculationService.calculateMetrics(transactions);
        
        String explanation = insightsService.explainAnomaly(anomalyType, metrics);
        
        return ResponseEntity.ok(Map.of(
            "merchantId", merchantId,
            "anomalyType", anomalyType,
            "explanation", explanation
        ));
    }

    /**
     * Get executive summary.
     */
    @GetMapping("/{merchantId}/summary")
    @Operation(
        summary = "Get executive summary",
        description = "Generate concise AI executive summary for the credit assessment"
    )
    public ResponseEntity<?> getExecutiveSummary(@PathVariable String merchantId) {
        log.info("Generating executive summary for merchant: {}", merchantId);
        
        Optional<CreditAssessment> assessmentOpt = assessmentRepository
            .findTopByMerchantIdOrderByAssessmentDateDesc(merchantId);
        
        if (assessmentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        CreditAssessment assessment = assessmentOpt.get();
        List<UpiTransaction> transactions = upiPlatformClient.getTransactionsForLastMonths(merchantId, 12);
        FinancialMetrics metrics = metricsCalculationService.calculateMetrics(transactions);
        
        String summary = insightsService.generateExecutiveSummary(assessment, metrics);
        
        return ResponseEntity.ok(Map.of(
            "merchantId", merchantId,
            "creditScore", assessment.getCreditScore(),
            "riskCategory", assessment.getRiskCategory().name(),
            "isEligible", assessment.getIsEligible(),
            "executiveSummary", summary
        ));
    }

    /**
     * Health check for AI features.
     */
    @GetMapping("/health")
    @Operation(summary = "AI health check", description = "Check if AI/LLM features are available")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "AI Insights (FinGPT Integration)",
            "features", Map.of(
                "creditNarrative", true,
                "riskAnalysis", true,
                "recommendations", true,
                "chat", true,
                "comparativeAnalysis", true
            )
        ));
    }
}
