package com.lumexpay.vortexa.credit.controller;

import com.lumexpay.vortexa.credit.client.MockTransactionDataProvider;
import com.lumexpay.vortexa.credit.client.UpiPlatformClient;
import com.lumexpay.vortexa.credit.dto.CreditAssessmentResponse;
import com.lumexpay.vortexa.credit.dto.FinancialMetrics;
import com.lumexpay.vortexa.credit.dto.UpiTransaction;
import com.lumexpay.vortexa.credit.model.CreditAssessment;
import com.lumexpay.vortexa.credit.repository.CreditAssessmentRepository;
import com.lumexpay.vortexa.credit.service.CreditScoringService;
import com.lumexpay.vortexa.credit.service.MetricsCalculationService;
import com.lumexpay.vortexa.credit.service.PdfReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Demo controller for testing the Credit Assessment Engine with different scenarios.
 * This controller is useful for demonstrations and testing without a real UPI platform.
 */
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Demo", description = "Demo APIs for testing credit assessment scenarios")
public class DemoController {

    private final MockTransactionDataProvider mockDataProvider;
    private final MetricsCalculationService metricsCalculationService;
    private final CreditScoringService creditScoringService;
    private final PdfReportService pdfReportService;
    private final CreditAssessmentRepository repository;

    /**
     * Run credit assessment for a specific scenario.
     * 
     * Available scenarios:
     * - EXCELLENT: Low risk merchant with high volume, consistent business
     * - GOOD: Medium risk merchant with moderate volume
     * - POOR: High risk merchant with low volume, high bounce rate
     * - GROWING: Merchant showing strong growth
     * - DECLINING: Merchant with declining volume
     * - SEASONAL: Seasonal business with high variation
     * - STARTUP: New business with limited history
     * - INELIGIBLE: Merchant that doesn't meet minimum criteria
     */
    @PostMapping("/assess/{scenario}/{merchantId}")
    @Operation(
        summary = "Demo: Assess merchant with specific scenario",
        description = "Run credit assessment using mock data for a specific business scenario"
    )
    public ResponseEntity<?> assessScenario(
            @Parameter(description = "Scenario type", example = "EXCELLENT")
            @PathVariable String scenario,
            @Parameter(description = "Merchant ID", example = "DEMO_MERCHANT_001")
            @PathVariable String merchantId,
            @Parameter(description = "Months of history to generate", example = "12")
            @RequestParam(defaultValue = "12") int months) {
        
        log.info("Running demo assessment for scenario: {} merchant: {}", scenario, merchantId);
        
        try {
            // Generate mock transactions for the scenario
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusMonths(months);
            List<UpiTransaction> transactions = mockDataProvider.generateScenario(
                scenario, merchantId, startDate, endDate);
            
            log.info("Generated {} transactions for scenario: {}", transactions.size(), scenario);
            
            // Calculate metrics
            FinancialMetrics metrics = metricsCalculationService.calculateMetrics(transactions);
            
            // Calculate scores
            CreditAssessment assessment = creditScoringService.calculateScores(metrics, merchantId);
            
            // Generate PDF report
            try {
                String reportUrl = pdfReportService.generateReport(assessment);
                assessment.setReportUrl(reportUrl);
            } catch (IOException e) {
                log.warn("Failed to generate PDF report: {}", e.getMessage());
            }
            
            // Save assessment
            assessment = repository.save(assessment);
            
            return ResponseEntity.ok(CreditAssessmentResponse.fromEntity(assessment));
            
        } catch (Exception e) {
            log.error("Demo assessment failed for scenario: {}", scenario, e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "error", "Demo assessment failed",
                    "message", e.getMessage(),
                    "scenario", scenario
                ));
        }
    }

    /**
     * Get available scenarios.
     */
    @GetMapping("/scenarios")
    @Operation(summary = "List available demo scenarios")
    public ResponseEntity<Map<String, String>> getScenarios() {
        return ResponseEntity.ok(Map.of(
            "EXCELLENT", "Low risk merchant - High volume, consistent, low bounce rate",
            "GOOD", "Medium risk merchant - Moderate volume, acceptable metrics",
            "POOR", "High risk merchant - Low volume, high bounce rate",
            "GROWING", "Growing business - Strong month-over-month growth",
            "DECLINING", "Declining business - Negative growth trend",
            "SEASONAL", "Seasonal business - High variation between months",
            "STARTUP", "New business - Limited transaction history",
            "INELIGIBLE", "Ineligible merchant - Below minimum thresholds"
        ));
    }

    /**
     * Preview mock transactions without running full assessment.
     */
    @GetMapping("/preview/{scenario}/{merchantId}")
    @Operation(summary = "Preview mock transactions for a scenario")
    public ResponseEntity<?> previewTransactions(
            @PathVariable String scenario,
            @PathVariable String merchantId,
            @RequestParam(defaultValue = "3") int months,
            @RequestParam(defaultValue = "50") int limit) {
        
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(months);
        List<UpiTransaction> transactions = mockDataProvider.generateScenario(
            scenario, merchantId, startDate, endDate);
        
        // Return limited transactions with summary
        List<UpiTransaction> sample = transactions.stream().limit(limit).toList();
        
        FinancialMetrics metrics = metricsCalculationService.calculateMetrics(transactions);
        
        return ResponseEntity.ok(Map.of(
            "scenario", scenario,
            "merchantId", merchantId,
            "totalTransactions", transactions.size(),
            "sampleTransactions", sample,
            "summary", Map.of(
                "last3MonthsVolume", metrics.getLast3MonthsVolume(),
                "averageMonthlyVolume", metrics.getAverageMonthlyVolume(),
                "bounceRate", metrics.getBounceRate(),
                "growthRate", metrics.getGrowthRate(),
                "uniqueCustomers", metrics.getUniqueCustomerCount()
            )
        ));
    }

    /**
     * Run all scenarios and compare results.
     */
    @PostMapping("/compare-all")
    @Operation(summary = "Run all scenarios and compare results")
    public ResponseEntity<?> compareAllScenarios(
            @RequestParam(defaultValue = "12") int months) {
        
        String[] scenarios = {"EXCELLENT", "GOOD", "POOR", "GROWING", "DECLINING", "SEASONAL", "STARTUP", "INELIGIBLE"};
        
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        
        for (String scenario : scenarios) {
            try {
                String merchantId = "DEMO_" + scenario + "_001";
                LocalDate endDate = LocalDate.now();
                LocalDate startDate = endDate.minusMonths(months);
                
                List<UpiTransaction> transactions = mockDataProvider.generateScenario(
                    scenario, merchantId, startDate, endDate);
                FinancialMetrics metrics = metricsCalculationService.calculateMetrics(transactions);
                CreditAssessment assessment = creditScoringService.calculateScores(metrics, merchantId);
                
                results.add(Map.of(
                    "scenario", scenario,
                    "merchantId", merchantId,
                    "creditScore", assessment.getCreditScore(),
                    "riskCategory", assessment.getRiskCategory().name(),
                    "isEligible", assessment.getIsEligible(),
                    "eligibleLoanAmount", assessment.getEligibleLoanAmount() != null ? 
                        assessment.getEligibleLoanAmount().toString() : "0",
                    "maxTenureDays", assessment.getMaxTenureDays() != null ? 
                        assessment.getMaxTenureDays() : 0,
                    "recommendedInterestRate", assessment.getRecommendedInterestRate() != null ?
                        assessment.getRecommendedInterestRate().toString() + "%" : "N/A",
                    "metrics", Map.of(
                        "avgMonthlyVolume", metrics.getAverageMonthlyVolume(),
                        "bounceRate", metrics.getBounceRate(),
                        "growthRate", metrics.getGrowthRate(),
                        "consistencyScore", metrics.getConsistencyScore()
                    )
                ));
            } catch (Exception e) {
                results.add(Map.of(
                    "scenario", scenario,
                    "error", e.getMessage()
                ));
            }
        }
        
        return ResponseEntity.ok(Map.of(
            "comparison", results,
            "generatedAt", java.time.LocalDateTime.now().toString()
        ));
    }

    /**
     * Health check for demo endpoints.
     */
    @GetMapping("/health")
    @Operation(summary = "Demo health check")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "mode", "DEMO",
            "message", "Demo endpoints are ready for testing"
        ));
    }
}
