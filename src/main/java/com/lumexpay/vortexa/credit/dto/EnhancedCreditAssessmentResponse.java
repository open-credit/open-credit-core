package com.lumexpay.vortexa.credit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lumexpay.vortexa.credit.service.CreditInsightsService.CreditInsights;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Enhanced credit assessment response that includes AI-generated insights.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedCreditAssessmentResponse {

    /**
     * Standard credit assessment data
     */
    @JsonProperty("assessment")
    private CreditAssessmentResponse assessment;

    /**
     * AI-generated insights
     */
    @JsonProperty("ai_insights")
    private AiInsights aiInsights;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiInsights {
        
        @JsonProperty("executive_summary")
        private String executiveSummary;

        @JsonProperty("credit_narrative")
        private String creditNarrative;

        @JsonProperty("risk_analysis")
        private String riskAnalysis;

        @JsonProperty("recommendations")
        private String recommendations;

        @JsonProperty("improvement_plan")
        private String improvementPlan;

        @JsonProperty("ai_generated")
        private boolean aiGenerated;

        /**
         * Create from CreditInsights
         */
        public static AiInsights fromCreditInsights(CreditInsights insights) {
            if (insights == null) {
                return AiInsights.builder()
                    .aiGenerated(false)
                    .executiveSummary("AI insights unavailable")
                    .build();
            }
            return AiInsights.builder()
                .executiveSummary(insights.getExecutiveSummary())
                .creditNarrative(insights.getCreditNarrative())
                .riskAnalysis(insights.getRiskAnalysis())
                .recommendations(insights.getRecommendations())
                .improvementPlan(insights.getImprovementPlan())
                .aiGenerated(insights.isAiGenerated())
                .build();
        }
    }
}
