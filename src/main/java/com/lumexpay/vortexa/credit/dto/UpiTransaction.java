package com.lumexpay.vortexa.credit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO representing a UPI transaction fetched from the collection platform.
 * This is the input data used for credit assessment calculations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpiTransaction {

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("merchant_id")
    private String merchantId;

    @JsonProperty("transaction_date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime transactionDate;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("payer_vpa")
    private String payerVpa;

    @JsonProperty("transaction_type")
    private TransactionType transactionType;

    @JsonProperty("status")
    private TransactionStatus status;

    @JsonProperty("merchant_category")
    private String merchantCategory;

    /**
     * Transaction type enum
     */
    public enum TransactionType {
        CREDIT,  // Money received by merchant
        DEBIT    // Money paid by merchant
    }

    /**
     * Transaction status enum
     */
    public enum TransactionStatus {
        SUCCESS,
        FAILED,
        PENDING
    }
}
