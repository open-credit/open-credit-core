package com.lumexpay.vortexa.credit.controller;

import com.lumexpay.vortexa.credit.dto.CreditAssessmentResponse;
import com.lumexpay.vortexa.credit.dto.EligibilityResponse;
import com.lumexpay.vortexa.credit.dto.EnhancedCreditAssessmentResponse;
import com.lumexpay.vortexa.credit.dto.FinancialMetrics;
import com.lumexpay.vortexa.credit.dto.UpiTransaction;
import com.lumexpay.vortexa.credit.model.CreditAssessment;
import com.lumexpay.vortexa.credit.client.UpiPlatformClient;
import com.lumexpay.vortexa.credit.repository.CreditAssessmentRepository;
import com.lumexpay.vortexa.credit.service.CreditAssessmentService;
import com.lumexpay.vortexa.credit.service.CreditInsightsService;
import com.lumexpay.vortexa.credit.service.MetricsCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST API controller for credit assessment operations.
 * Enhanced with AI-powered insights via FinGPT integration.
 */
@RestController
@RequestMapping("/credit-assessment")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Credit Assessment", description = "APIs for merchant credit assessment and eligibility")
public class CreditAssessmentController {

    private final CreditAssessmentService creditAssessmentService;
    private final CreditInsightsService insightsService;
    private final CreditAssessmentRepository assessmentRepository;
    private final UpiPlatformClient upiPlatformClient;
    private final MetricsCalculationService metricsCalculationService;

    /**
     * Trigger credit assessment for a merchant.
     */
    @PostMapping("/analyze/{merchantId}")
    @Operation(
        summary = "Analyze merchant credit",
        description = "Triggers a complete credit assessment for the specified merchant based on their UPI transaction history"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Assessment completed successfully",
            content = @Content(schema = @Schema(implementation = CreditAssessmentResponse.class))),
        @ApiResponse(responseCode = "500", description = "Assessment failed")
    })
    public ResponseEntity<?> analyzeMerchant(
            @Parameter(description = "Merchant ID", required = true)
            @PathVariable String merchantId) {
        
        log.info("Received credit assessment request for merchant: {}", merchantId);
        
        try {
            CreditAssessmentResponse response = creditAssessmentService.assess(merchantId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Credit assessment failed for merchant: {}", merchantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Credit assessment failed",
                    "message", e.getMessage(),
                    "merchantId", merchantId
                ));
        }
    }

    /**
     * Get the latest credit assessment report for a merchant.
     */
    @GetMapping("/report/{merchantId}")
    @Operation(
        summary = "Get credit report",
        description = "Retrieves the latest credit assessment report for a merchant"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Report found",
            content = @Content(schema = @Schema(implementation = CreditAssessmentResponse.class))),
        @ApiResponse(responseCode = "404", description = "No assessment found for merchant")
    })
    public ResponseEntity<?> getCreditReport(
            @Parameter(description = "Merchant ID", required = true)
            @PathVariable String merchantId) {
        
        log.info("Fetching credit report for merchant: {}", merchantId);
        
        return creditAssessmentService.getLatestAssessment(merchantId)
            .map(response -> ResponseEntity.ok(response))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body((CreditAssessmentResponse) null));
    }

    /**
     * Get assessment by UUID.
     */
    @GetMapping("/assessment/{assessmentId}")
    @Operation(
        summary = "Get assessment by ID",
        description = "Retrieves a specific credit assessment by its UUID"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Assessment found"),
        @ApiResponse(responseCode = "404", description = "Assessment not found")
    })
    public ResponseEntity<?> getAssessmentById(
            @Parameter(description = "Assessment UUID", required = true)
            @PathVariable UUID assessmentId) {
        
        log.info("Fetching assessment by ID: {}", assessmentId);
        
        return creditAssessmentService.getAssessmentById(assessmentId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Get assessment history for a merchant.
     */
    @GetMapping("/history/{merchantId}")
    @Operation(
        summary = "Get assessment history",
        description = "Retrieves all credit assessments for a merchant"
    )
    public ResponseEntity<List<CreditAssessmentResponse>> getAssessmentHistory(
            @Parameter(description = "Merchant ID", required = true)
            @PathVariable String merchantId) {
        
        log.info("Fetching assessment history for merchant: {}", merchantId);
        List<CreditAssessmentResponse> history = creditAssessmentService.getAssessmentHistory(merchantId);
        return ResponseEntity.ok(history);
    }

    /**
     * Get loan eligibility for a merchant.
     */
    @GetMapping("/eligibility/{merchantId}")
    @Operation(
        summary = "Get loan eligibility",
        description = "Retrieves loan eligibility details and recommendations for a merchant"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Eligibility information found",
            content = @Content(schema = @Schema(implementation = EligibilityResponse.class))),
        @ApiResponse(responseCode = "404", description = "No assessment found - run assessment first")
    })
    public ResponseEntity<?> getEligibility(
            @Parameter(description = "Merchant ID", required = true)
            @PathVariable String merchantId) {
        
        log.info("Fetching eligibility for merchant: {}", merchantId);
        
        return creditAssessmentService.getEligibility(merchantId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body((EligibilityResponse) null));
    }

    /**
     * Download PDF credit report.
     */
    @GetMapping("/report/{merchantId}/pdf")
    @Operation(
        summary = "Download PDF report",
        description = "Downloads the PDF credit report for a merchant"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PDF report",
            content = @Content(mediaType = "application/pdf")),
        @ApiResponse(responseCode = "404", description = "Report not found")
    })
    public ResponseEntity<Resource> downloadPdfReport(
            @Parameter(description = "Merchant ID", required = true)
            @PathVariable String merchantId) {
        
        log.info("Downloading PDF report for merchant: {}", merchantId);
        
        String filePath = creditAssessmentService.getReportFilePath(merchantId);
        
        if (filePath == null) {
            return ResponseEntity.notFound().build();
        }
        
        File file = new File(filePath);
        if (!file.exists()) {
            log.warn("PDF file not found at path: {}", filePath);
            return ResponseEntity.notFound().build();
        }
        
        Resource resource = new FileSystemResource(file);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"" + file.getName() + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(file.length())
            .body(resource);
    }

    /**
     * Re-assess merchant credit (manual trigger).
     */
    @PostMapping("/re-assess/{merchantId}")
    @Operation(
        summary = "Re-assess merchant credit",
        description = "Manually triggers a credit re-assessment for a merchant"
    )
    public ResponseEntity<?> reAssessMerchant(
            @Parameter(description = "Merchant ID", required = true)
            @PathVariable String merchantId) {
        
        log.info("Manual re-assessment triggered for merchant: {}", merchantId);
        
        try {
            CreditAssessmentResponse response = creditAssessmentService.reassess(merchantId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Re-assessment failed for merchant: {}", merchantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Re-assessment failed",
                    "message", e.getMessage()
                ));
        }
    }

    /**
     * Get all eligible merchants.
     */
    @GetMapping("/eligible")
    @Operation(
        summary = "Get eligible merchants",
        description = "Retrieves all merchants that are eligible for loans"
    )
    public ResponseEntity<List<CreditAssessmentResponse>> getEligibleMerchants() {
        log.info("Fetching all eligible merchants");
        return ResponseEntity.ok(creditAssessmentService.getEligibleMerchants());
    }

    /**
     * Get merchants by risk category.
     */
    @GetMapping("/by-risk/{riskCategory}")
    @Operation(
        summary = "Get merchants by risk category",
        description = "Retrieves merchants filtered by risk category (LOW, MEDIUM, HIGH)"
    )
    public ResponseEntity<?> getMerchantsByRisk(
            @Parameter(description = "Risk category", required = true)
            @PathVariable String riskCategory) {
        
        log.info("Fetching merchants with risk category: {}", riskCategory);
        
        try {
            CreditAssessment.RiskCategory category = CreditAssessment.RiskCategory.valueOf(riskCategory.toUpperCase());
            return ResponseEntity.ok(creditAssessmentService.getMerchantsByRiskCategory(category));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "error", "Invalid risk category",
                    "validCategories", List.of("LOW", "MEDIUM", "HIGH")
                ));
        }
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the service is running")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "Credit Assessment Engine",
            "version", "1.0.0",
            "aiFeatures", "enabled"
        ));
    }

    /**
     * Enhanced assessment with AI insights.
     */
    @PostMapping("/analyze-enhanced/{merchantId}")
    @Operation(
        summary = "Analyze with AI insights",
        description = "Triggers credit assessment with AI-powered insights, recommendations, and risk analysis"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Assessment with AI insights completed",
            content = @Content(schema = @Schema(implementation = EnhancedCreditAssessmentResponse.class))),
        @ApiResponse(responseCode = "500", description = "Assessment failed")
    })
    public ResponseEntity<?> analyzeMerchantEnhanced(
            @Parameter(description = "Merchant ID", required = true)
            @PathVariable String merchantId,
            @Parameter(description = "Include AI insights")
            @RequestParam(defaultValue = "true") boolean includeInsights) {
        
        log.info("Received enhanced credit assessment request for merchant: {}", merchantId);
        
        try {
            // Run standard assessment
            CreditAssessmentResponse assessment = creditAssessmentService.assess(merchantId);
            
            EnhancedCreditAssessmentResponse.AiInsights aiInsights = null;
            
            if (includeInsights) {
                // Get the assessment entity for insights generation
                Optional<CreditAssessment> entityOpt = assessmentRepository
                    .findTopByMerchantIdOrderByAssessmentDateDesc(merchantId);
                
                if (entityOpt.isPresent()) {
                    // Fetch transactions for metrics
                    List<UpiTransaction> transactions = upiPlatformClient
                        .getTransactionsForLastMonths(merchantId, 12);
                    FinancialMetrics metrics = metricsCalculationService.calculateMetrics(transactions);
                    
                    // Generate AI insights
                    CreditInsightsService.CreditInsights insights = 
                        insightsService.generateInsights(entityOpt.get(), metrics);
                    
                    aiInsights = EnhancedCreditAssessmentResponse.AiInsights.fromCreditInsights(insights);
                }
            }
            
            EnhancedCreditAssessmentResponse response = EnhancedCreditAssessmentResponse.builder()
                .assessment(assessment)
                .aiInsights(aiInsights)
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Enhanced credit assessment failed for merchant: {}", merchantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Enhanced credit assessment failed",
                    "message", e.getMessage(),
                    "merchantId", merchantId
                ));
        }
    }
}
