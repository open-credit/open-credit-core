package com.lumexpay.vortexa.credit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * API response DTO for eligibility queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibilityResponse {

    @JsonProperty("merchant_id")
    private String merchantId;

    @JsonProperty("is_eligible")
    private Boolean isEligible;

    @JsonProperty("credit_score")
    private Integer creditScore;

    @JsonProperty("risk_category")
    private String riskCategory;

    @JsonProperty("eligible_loan_amount")
    private BigDecimal eligibleLoanAmount;

    @JsonProperty("eligible_loan_amount_formatted")
    private String eligibleLoanAmountFormatted;

    @JsonProperty("max_tenure_days")
    private Integer maxTenureDays;

    @JsonProperty("max_tenure_months")
    private Integer maxTenureMonths;

    @JsonProperty("recommended_interest_rate")
    private BigDecimal recommendedInterestRate;

    @JsonProperty("ineligibility_reason")
    private String ineligibilityReason;

    @JsonProperty("assessment_date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime assessmentDate;

    @JsonProperty("recommendations")
    private LoanRecommendations recommendations;

    /**
     * Loan recommendations
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoanRecommendations {
        @JsonProperty("recommended_emi")
        private BigDecimal recommendedEmi;

        @JsonProperty("max_monthly_repayment")
        private BigDecimal maxMonthlyRepayment;

        @JsonProperty("suggested_loan_tenure_days")
        private Integer suggestedLoanTenureDays;

        @JsonProperty("comments")
        private String comments;
    }
}
