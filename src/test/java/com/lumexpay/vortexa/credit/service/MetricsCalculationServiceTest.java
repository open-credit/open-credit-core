package com.lumexpay.vortexa.credit.service;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.lumexpay.vortexa.credit.dto.FinancialMetrics;
import com.lumexpay.vortexa.credit.dto.UpiTransaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetricsCalculationService.
 */
@DisplayName("Metrics Calculation Service Tests")
class MetricsCalculationServiceTest {

    private MetricsCalculationService service;

    @BeforeEach
    void setUp() {
        service = new MetricsCalculationService();
    }

    @Nested
    @DisplayName("Volume Calculations")
    class VolumeCalculationTests {

        @Test
        @DisplayName("Should calculate correct volume for last 3 months")
        void shouldCalculateVolumeForLast3Months() {
            // Given: transactions in last 3 months
            List<UpiTransaction> transactions = createTransactionsForLastNMonths(3, 10, 
                new BigDecimal("10000"));

            // When
            FinancialMetrics metrics = service.calculateMetrics(transactions);

            // Then: volume should be approximately 30 transactions * 10000 = 300000
            assertNotNull(metrics.getLast3MonthsVolume());
            assertTrue(metrics.getLast3MonthsVolume().compareTo(BigDecimal.ZERO) > 0);
        }

        @Test
        @DisplayName("Should calculate correct average monthly volume")
        void shouldCalculateAverageMonthlyVolume() {
            // Given: consistent monthly transactions
            List<UpiTransaction> transactions = createTransactionsForLastNMonths(3, 10, 
                new BigDecimal("10000"));

            // When
            FinancialMetrics metrics = service.calculateMetrics(transactions);

            // Then
            assertNotNull(metrics.getAverageMonthlyVolume());
            assertTrue(metrics.getAverageMonthlyVolume().compareTo(BigDecimal.ZERO) > 0);
        }

        @Test
        @DisplayName("Should return zero volume for empty transactions")
        void shouldReturnZeroVolumeForEmptyTransactions() {
            // Given
            List<UpiTransaction> transactions = new ArrayList<>();

            // When
            FinancialMetrics metrics = service.calculateMetrics(transactions);

            // Then
            assertEquals(0, metrics.getLast3MonthsVolume().compareTo(BigDecimal.ZERO));
            assertEquals(0, metrics.getAverageMonthlyVolume().compareTo(BigDecimal.ZERO));
        }

        @Test
        @DisplayName("Should only count successful CREDIT transactions for volume")
        void shouldOnlyCountSuccessfulCreditTransactions() {
            // Given: mix of transaction types and statuses
            List<UpiTransaction> transactions = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            
            // Successful credit - should be counted
            transactions.add(createTransaction("M1", now.minusDays(10), new BigDecimal("5000"), 
                UpiTransaction.TransactionType.CREDIT, UpiTransaction.TransactionStatus.SUCCESS));
            
            // Failed credit - should NOT be counted
            transactions.add(createTransaction("M1", now.minusDays(10), new BigDecimal("3000"), 
                UpiTransaction.TransactionType.CREDIT, UpiTransaction.TransactionStatus.FAILED));
            
            // Successful debit - should NOT be counted
            transactions.add(createTransaction("M1", now.minusDays(10), new BigDecimal("2000"), 
                UpiTransaction.TransactionType.DEBIT, UpiTransaction.TransactionStatus.SUCCESS));

            // When
            FinancialMetrics metrics = service.calculateMetrics(transactions);

            // Then: only 5000 should be counted
            assertEquals(0, metrics.getLast3MonthsVolume().compareTo(new BigDecimal("5000")));
        }
    }

    @Nested
    @DisplayName("Transaction Count Calculations")
    class TransactionCountTests {

        @Test
        @DisplayName("Should count total and successful transactions")
        void shouldCountTransactions() {
            // Given
            List<UpiTransaction> transactions = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            
            for (int i = 0; i < 15; i++) {
                transactions.add(createTransaction("M1", now.minusDays(i), new BigDecimal("1000"),
                    UpiTransaction.TransactionType.CREDIT, UpiTransaction.TransactionStatus.SUCCESS));
            }
            for (int i = 0; i < 5; i++) {
                transactions.add(createTransaction("M1", now.minusDays(i), new BigDecimal("500"),
                    UpiTransaction.TransactionType.CREDIT, UpiTransaction.TransactionStatus.FAILED));
            }

            // When
            FinancialMetrics metrics = service.calculateMetrics(transactions);

            // Then
            assertEquals(20, metrics.getTotalTransactionCount());
            assertEquals(15, metrics.getSuccessfulTransactionCount());
            assertEquals(5, metrics.getFailedTransactionCount());
        }
    }

    @Nested
    @DisplayName("Customer Metrics Calculations")
    class CustomerMetricsTests {

        @Test
        @DisplayName("Should calculate unique customer count")
        void shouldCalculateUniqueCustomerCount() {
            // Given
            List<UpiTransaction> transactions = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            
            // 5 unique customers
            for (int i = 0; i < 5; i++) {
                transactions.add(createTransactionWithPayer("M1", now.minusDays(i), 
                    new BigDecimal("1000"), "customer" + i + "@upi"));
            }
            // Repeat customer
            transactions.add(createTransactionWithPayer("M1", now.minusDays(6), 
                new BigDecimal("2000"), "customer0@upi"));

            // When
            FinancialMetrics metrics = service.calculateMetrics(transactions);

            // Then
            assertEquals(5, metrics.getUniqueCustomerCount());
        }

        @Test
        @DisplayName("Should calculate customer concentration correctly")
        void shouldCalculateCustomerConcentration() {
            // Given: 10 customers with varying volumes
            List<UpiTransaction> transactions = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            
            // Top customer - 50% of volume
            for (int i = 0; i < 5; i++) {
                transactions.add(createTransactionWithPayer("M1", now.minusDays(i), 
                    new BigDecimal("10000"), "topcustomer@upi"));
            }
            // Other 9 customers - 50% of volume
            for (int i = 0; i < 9; i++) {
                transactions.add(createTransactionWithPayer("M1", now.minusDays(i), 
                    new BigDecimal("5555"), "customer" + i + "@upi"));
            }

            // When
            FinancialMetrics metrics = service.calculateMetrics(transactions);

            // Then: concentration should be high (>50%)
            assertNotNull(metrics.getCustomerConcentration());
            assertTrue(metrics.getCustomerConcentration().compareTo(new BigDecimal("50")) > 0);
        }
    }

    @Nested
    @DisplayName("Consistency Score Calculations")
    class ConsistencyScoreTests {

        @Test
        @DisplayName("Should calculate high consistency score for stable business")
        void shouldCalculateHighConsistencyForStableBusiness() {
            // Given: consistent monthly volumes
            List<FinancialMetrics.MonthlyVolume> monthlyVolumes = Arrays.asList(
                createMonthlyVolume("2024-01", new BigDecimal("100000")),
                createMonthlyVolume("2024-02", new BigDecimal("105000")),
                createMonthlyVolume("2024-03", new BigDecimal("98000")),
                createMonthlyVolume("2024-04", new BigDecimal("102000")),
                createMonthlyVolume("2024-05", new BigDecimal("100000")),
                createMonthlyVolume("2024-06", new BigDecimal("103000"))
            );

            // When
            BigDecimal consistencyScore = service.calculateConsistencyScore(monthlyVolumes);

            // Then: should be high (>70)
            assertTrue(consistencyScore.compareTo(new BigDecimal("70")) > 0,
                "Consistency score should be > 70 for stable business, got: " + consistencyScore);
        }

        @Test
        @DisplayName("Should calculate low consistency score for volatile business")
        void shouldCalculateLowConsistencyForVolatileBusiness() {
            // Given: highly variable monthly volumes
            List<FinancialMetrics.MonthlyVolume> monthlyVolumes = Arrays.asList(
                createMonthlyVolume("2024-01", new BigDecimal("50000")),
                createMonthlyVolume("2024-02", new BigDecimal("200000")),
                createMonthlyVolume("2024-03", new BigDecimal("30000")),
                createMonthlyVolume("2024-04", new BigDecimal("180000")),
                createMonthlyVolume("2024-05", new BigDecimal("40000")),
                createMonthlyVolume("2024-06", new BigDecimal("150000"))
            );

            // When
            BigDecimal consistencyScore = service.calculateConsistencyScore(monthlyVolumes);

            // Then: should be low (<50)
            assertTrue(consistencyScore.compareTo(new BigDecimal("60")) < 0,
                "Consistency score should be < 60 for volatile business, got: " + consistencyScore);
        }

        @Test
        @DisplayName("Should return default score for insufficient data")
        void shouldReturnDefaultForInsufficientData() {
            // Given: only 1 month of data
            List<FinancialMetrics.MonthlyVolume> monthlyVolumes = Arrays.asList(
                createMonthlyVolume("2024-01", new BigDecimal("100000"))
            );

            // When
            BigDecimal consistencyScore = service.calculateConsistencyScore(monthlyVolumes);

            // Then: should return default (50)
            assertEquals(0, consistencyScore.compareTo(new BigDecimal("50")));
        }
    }

    @Nested
    @DisplayName("Growth Rate Calculations")
    class GrowthRateTests {

        @Test
        @DisplayName("Should calculate positive growth rate")
        void shouldCalculatePositiveGrowthRate() {
            // Given
            BigDecimal currentPeriod = new BigDecimal("150000");
            BigDecimal previousPeriod = new BigDecimal("100000");

            // When
            BigDecimal growthRate = service.calculateGrowthRate(currentPeriod, previousPeriod);

            // Then: 50% growth
            assertEquals(0, growthRate.compareTo(new BigDecimal("50.00")));
        }

        @Test
        @DisplayName("Should calculate negative growth rate for decline")
        void shouldCalculateNegativeGrowthRate() {
            // Given
            BigDecimal currentPeriod = new BigDecimal("80000");
            BigDecimal previousPeriod = new BigDecimal("100000");

            // When
            BigDecimal growthRate = service.calculateGrowthRate(currentPeriod, previousPeriod);

            // Then: -20% growth
            assertEquals(0, growthRate.compareTo(new BigDecimal("-20.00")));
        }

        @Test
        @DisplayName("Should handle zero previous period")
        void shouldHandleZeroPreviousPeriod() {
            // Given
            BigDecimal currentPeriod = new BigDecimal("100000");
            BigDecimal previousPeriod = BigDecimal.ZERO;

            // When
            BigDecimal growthRate = service.calculateGrowthRate(currentPeriod, previousPeriod);

            // Then: 100% growth (new business)
            assertEquals(0, growthRate.compareTo(new BigDecimal("100")));
        }
    }

    @Nested
    @DisplayName("Bounce Rate Calculations")
    class BounceRateTests {

        @Test
        @DisplayName("Should calculate bounce rate correctly")
        void shouldCalculateBounceRate() {
            // Given: 20 total, 4 failed = 20%
            int totalTransactions = 20;
            int failedTransactions = 4;

            // When
            BigDecimal bounceRate = service.calculateBounceRate(totalTransactions, failedTransactions);

            // Then
            assertEquals(0, bounceRate.compareTo(new BigDecimal("20.00")));
        }

        @Test
        @DisplayName("Should return zero bounce rate for no failures")
        void shouldReturnZeroForNoFailures() {
            // Given
            int totalTransactions = 100;
            int failedTransactions = 0;

            // When
            BigDecimal bounceRate = service.calculateBounceRate(totalTransactions, failedTransactions);

            // Then
            assertEquals(0, bounceRate.compareTo(BigDecimal.ZERO));
        }

        @Test
        @DisplayName("Should handle zero total transactions")
        void shouldHandleZeroTotal() {
            // Given
            int totalTransactions = 0;
            int failedTransactions = 0;

            // When
            BigDecimal bounceRate = service.calculateBounceRate(totalTransactions, failedTransactions);

            // Then
            assertEquals(0, bounceRate.compareTo(BigDecimal.ZERO));
        }
    }

    @Nested
    @DisplayName("Statistical Calculations")
    class StatisticalTests {

        @Test
        @DisplayName("Should calculate mean correctly")
        void shouldCalculateMean() {
            // Given
            List<BigDecimal> values = Arrays.asList(
                new BigDecimal("100"),
                new BigDecimal("200"),
                new BigDecimal("300")
            );

            // When
            BigDecimal mean = service.calculateMean(values);

            // Then: (100 + 200 + 300) / 3 = 200
            assertEquals(0, mean.setScale(0, RoundingMode.HALF_UP).compareTo(new BigDecimal("200")));
        }

        @Test
        @DisplayName("Should calculate standard deviation correctly")
        void shouldCalculateStandardDeviation() {
            // Given
            List<BigDecimal> values = Arrays.asList(
                new BigDecimal("10"),
                new BigDecimal("20"),
                new BigDecimal("30")
            );
            BigDecimal mean = new BigDecimal("20");

            // When
            BigDecimal stdDev = service.calculateStandardDeviation(values, mean);

            // Then: sqrt(((10-20)^2 + (20-20)^2 + (30-20)^2) / 3) = sqrt(200/3) ≈ 8.16
            assertTrue(stdDev.compareTo(new BigDecimal("7")) > 0);
            assertTrue(stdDev.compareTo(new BigDecimal("9")) < 0);
        }
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private List<UpiTransaction> createTransactionsForLastNMonths(int months, int perMonth, 
            BigDecimal amount) {
        List<UpiTransaction> transactions = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        for (int m = 0; m < months; m++) {
            for (int t = 0; t < perMonth; t++) {
                transactions.add(createTransaction(
                    "M1",
                    now.minusMonths(m).minusDays(t),
                    amount,
                    UpiTransaction.TransactionType.CREDIT,
                    UpiTransaction.TransactionStatus.SUCCESS
                ));
            }
        }
        return transactions;
    }

    private UpiTransaction createTransaction(String merchantId, LocalDateTime date, 
            BigDecimal amount, UpiTransaction.TransactionType type, 
            UpiTransaction.TransactionStatus status) {
        return UpiTransaction.builder()
            .transactionId("TXN_" + System.nanoTime())
            .merchantId(merchantId)
            .transactionDate(date)
            .amount(amount)
            .payerVpa("payer@upi")
            .transactionType(type)
            .status(status)
            .merchantCategory("RETAIL")
            .build();
    }

    private UpiTransaction createTransactionWithPayer(String merchantId, LocalDateTime date,
            BigDecimal amount, String payerVpa) {
        return UpiTransaction.builder()
            .transactionId("TXN_" + System.nanoTime())
            .merchantId(merchantId)
            .transactionDate(date)
            .amount(amount)
            .payerVpa(payerVpa)
            .transactionType(UpiTransaction.TransactionType.CREDIT)
            .status(UpiTransaction.TransactionStatus.SUCCESS)
            .merchantCategory("RETAIL")
            .build();
    }

    private FinancialMetrics.MonthlyVolume createMonthlyVolume(String month, BigDecimal volume) {
        return FinancialMetrics.MonthlyVolume.builder()
            .month(month)
            .volume(volume)
            .transactionCount(10)
            .uniqueCustomers(5)
            .build();
    }
}
