package com.lumexpay.vortexa.credit.service;

import com.lumexpay.vortexa.credit.dto.FinancialMetrics;
import com.lumexpay.vortexa.credit.dto.UpiTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating financial and performance metrics from UPI transactions.
 * This is the core calculation engine for credit assessment.
 */
@Service
@Slf4j
public class MetricsCalculationService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 4;

    /**
     * Calculate all financial metrics from transaction history.
     *
     * @param transactions List of UPI transactions
     * @return Calculated financial metrics
     */
    public FinancialMetrics calculateMetrics(List<UpiTransaction> transactions) {
        log.debug("Calculating metrics for {} transactions", transactions.size());

        if (transactions == null || transactions.isEmpty()) {
            return buildEmptyMetrics();
        }

        // Filter successful credit transactions for volume calculations
        List<UpiTransaction> successfulCredits = transactions.stream()
            .filter(t -> t.getTransactionType() == UpiTransaction.TransactionType.CREDIT)
            .filter(t -> t.getStatus() == UpiTransaction.TransactionStatus.SUCCESS)
            .collect(Collectors.toList());

        LocalDate now = LocalDate.now();

        // Calculate volume metrics
        BigDecimal last3MonthsVolume = calculateVolumeForPeriod(successfulCredits, now.minusMonths(3), now);
        BigDecimal last6MonthsVolume = calculateVolumeForPeriod(successfulCredits, now.minusMonths(6), now);
        BigDecimal last12MonthsVolume = calculateVolumeForPeriod(successfulCredits, now.minusMonths(12), now);
        BigDecimal previous3MonthsVolume = calculateVolumeForPeriod(successfulCredits, 
            now.minusMonths(6), now.minusMonths(3));

        // Calculate average monthly volume (based on last 3 months)
        BigDecimal averageMonthlyVolume = last3MonthsVolume.divide(
            new BigDecimal("3"), SCALE, RoundingMode.HALF_UP);

        // Calculate transaction counts
        int totalTransactionCount = transactions.size();
        int successfulCount = (int) transactions.stream()
            .filter(t -> t.getStatus() == UpiTransaction.TransactionStatus.SUCCESS)
            .count();
        int failedCount = (int) transactions.stream()
            .filter(t -> t.getStatus() == UpiTransaction.TransactionStatus.FAILED)
            .count();

        // Calculate average transaction value
        BigDecimal avgTransactionValue = BigDecimal.ZERO;
        if (successfulCount > 0) {
            avgTransactionValue = last12MonthsVolume.divide(
                new BigDecimal(successfulCredits.size() > 0 ? successfulCredits.size() : 1), 
                SCALE, RoundingMode.HALF_UP);
        }

        // Calculate customer metrics
        Map<String, BigDecimal> customerVolumes = calculateCustomerVolumes(successfulCredits);
        int uniqueCustomerCount = customerVolumes.size();
        BigDecimal top10CustomerVolume = calculateTop10CustomerVolume(customerVolumes);
        BigDecimal customerConcentration = calculateCustomerConcentration(top10CustomerVolume, last3MonthsVolume);

        // Calculate monthly breakdown
        List<FinancialMetrics.MonthlyVolume> monthlyVolumes = calculateMonthlyBreakdown(successfulCredits);

        // Calculate performance metrics
        BigDecimal consistencyScore = calculateConsistencyScore(monthlyVolumes);
        BigDecimal growthRate = calculateGrowthRate(last3MonthsVolume, previous3MonthsVolume);
        BigDecimal bounceRate = calculateBounceRate(totalTransactionCount, failedCount);

        // Detect seasonality
        BigDecimal coefficientOfVariation = calculateCoefficientOfVariation(monthlyVolumes);
        boolean isSeasonalBusiness = coefficientOfVariation.compareTo(new BigDecimal("0.50")) > 0;

        // Detect fraud indicators
        boolean hasSuddenVolumeSpike = detectSuddenVolumeSpike(monthlyVolumes);
        boolean hasLowCustomerDiversity = uniqueCustomerCount < 5;
        boolean hasSinglePayerDominance = customerConcentration.compareTo(new BigDecimal("80")) > 0;

        // Find peak and trough months
        String peakMonth = findPeakMonth(monthlyVolumes);
        String troughMonth = findTroughMonth(monthlyVolumes);

        return FinancialMetrics.builder()
            .last3MonthsVolume(last3MonthsVolume)
            .last6MonthsVolume(last6MonthsVolume)
            .last12MonthsVolume(last12MonthsVolume)
            .averageMonthlyVolume(averageMonthlyVolume)
            .averageTransactionValue(avgTransactionValue)
            .totalTransactionCount(totalTransactionCount)
            .successfulTransactionCount(successfulCount)
            .failedTransactionCount(failedCount)
            .uniqueCustomerCount(uniqueCustomerCount)
            .top10CustomerVolume(top10CustomerVolume)
            .customerConcentration(customerConcentration)
            .monthlyVolumes(monthlyVolumes)
            .customerVolumes(customerVolumes)
            .consistencyScore(consistencyScore)
            .growthRate(growthRate)
            .bounceRate(bounceRate)
            .previousPeriodVolume(previous3MonthsVolume)
            .isSeasonalBusiness(isSeasonalBusiness)
            .coefficientOfVariation(coefficientOfVariation)
            .peakMonth(peakMonth)
            .troughMonth(troughMonth)
            .hasSuddenVolumeSpike(hasSuddenVolumeSpike)
            .hasLowCustomerDiversity(hasLowCustomerDiversity)
            .hasSinglePayerDominance(hasSinglePayerDominance)
            .build();
    }

    /**
     * Calculate total volume for a specific period.
     */
    private BigDecimal calculateVolumeForPeriod(List<UpiTransaction> transactions, 
                                                 LocalDate startDate, 
                                                 LocalDate endDate) {
        return transactions.stream()
            .filter(t -> {
                LocalDate txDate = t.getTransactionDate().toLocalDate();
                return !txDate.isBefore(startDate) && !txDate.isAfter(endDate);
            })
            .map(UpiTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate customer volumes (group by payer VPA).
     */
    private Map<String, BigDecimal> calculateCustomerVolumes(List<UpiTransaction> transactions) {
        return transactions.stream()
            .filter(t -> t.getPayerVpa() != null)
            .collect(Collectors.groupingBy(
                UpiTransaction::getPayerVpa,
                Collectors.reducing(BigDecimal.ZERO, UpiTransaction::getAmount, BigDecimal::add)
            ));
    }

    /**
     * Calculate top 10 customers' total volume.
     */
    private BigDecimal calculateTop10CustomerVolume(Map<String, BigDecimal> customerVolumes) {
        return customerVolumes.values().stream()
            .sorted(Comparator.reverseOrder())
            .limit(10)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate customer concentration (top 10 as % of total).
     */
    private BigDecimal calculateCustomerConcentration(BigDecimal top10Volume, BigDecimal totalVolume) {
        if (totalVolume.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return top10Volume.divide(totalVolume, SCALE, RoundingMode.HALF_UP).multiply(HUNDRED);
    }

    /**
     * Calculate monthly volume breakdown.
     */
    private List<FinancialMetrics.MonthlyVolume> calculateMonthlyBreakdown(List<UpiTransaction> transactions) {
        Map<String, List<UpiTransaction>> byMonth = transactions.stream()
            .collect(Collectors.groupingBy(
                t -> t.getTransactionDate().format(MONTH_FORMATTER)
            ));

        return byMonth.entrySet().stream()
            .map(entry -> {
                String month = entry.getKey();
                List<UpiTransaction> monthTx = entry.getValue();
                BigDecimal volume = monthTx.stream()
                    .map(UpiTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                int txCount = monthTx.size();
                int uniqueCustomers = (int) monthTx.stream()
                    .map(UpiTransaction::getPayerVpa)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();

                return FinancialMetrics.MonthlyVolume.builder()
                    .month(month)
                    .volume(volume)
                    .transactionCount(txCount)
                    .uniqueCustomers(uniqueCustomers)
                    .build();
            })
            .sorted(Comparator.comparing(FinancialMetrics.MonthlyVolume::getMonth))
            .collect(Collectors.toList());
    }

    /**
     * Calculate consistency score (0-100) based on coefficient of variation.
     * Lower CV = higher consistency.
     */
    public BigDecimal calculateConsistencyScore(List<FinancialMetrics.MonthlyVolume> monthlyVolumes) {
        if (monthlyVolumes == null || monthlyVolumes.size() < 2) {
            return new BigDecimal("50"); // Default for insufficient data
        }

        List<BigDecimal> volumes = monthlyVolumes.stream()
            .map(FinancialMetrics.MonthlyVolume::getVolume)
            .collect(Collectors.toList());

        // Calculate mean
        BigDecimal mean = calculateMean(volumes);
        if (mean.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // Calculate standard deviation
        BigDecimal stdDev = calculateStandardDeviation(volumes, mean);

        // Calculate coefficient of variation (CV = stdDev / mean)
        BigDecimal cv = stdDev.divide(mean, SCALE, RoundingMode.HALF_UP);

        // Consistency score = 100 - (CV * 100), capped at 0-100
        BigDecimal consistencyScore = HUNDRED.subtract(cv.multiply(HUNDRED));
        consistencyScore = consistencyScore.max(BigDecimal.ZERO).min(HUNDRED);

        return consistencyScore.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate growth rate (percentage change).
     */
    public BigDecimal calculateGrowthRate(BigDecimal currentPeriod, BigDecimal previousPeriod) {
        if (previousPeriod == null || previousPeriod.compareTo(BigDecimal.ZERO) == 0) {
            if (currentPeriod.compareTo(BigDecimal.ZERO) > 0) {
                return HUNDRED; // 100% growth if coming from zero
            }
            return BigDecimal.ZERO;
        }

        return currentPeriod.subtract(previousPeriod)
            .divide(previousPeriod, SCALE, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate bounce rate (failed transactions percentage).
     */
    public BigDecimal calculateBounceRate(int totalTransactions, int failedTransactions) {
        if (totalTransactions == 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(failedTransactions)
            .divide(new BigDecimal(totalTransactions), SCALE, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate coefficient of variation.
     */
    private BigDecimal calculateCoefficientOfVariation(List<FinancialMetrics.MonthlyVolume> monthlyVolumes) {
        if (monthlyVolumes == null || monthlyVolumes.size() < 2) {
            return BigDecimal.ZERO;
        }

        List<BigDecimal> volumes = monthlyVolumes.stream()
            .map(FinancialMetrics.MonthlyVolume::getVolume)
            .collect(Collectors.toList());

        BigDecimal mean = calculateMean(volumes);
        if (mean.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal stdDev = calculateStandardDeviation(volumes, mean);
        return stdDev.divide(mean, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculate mean of a list of values.
     */
    public BigDecimal calculateMean(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(new BigDecimal(values.size()), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculate standard deviation.
     */
    public BigDecimal calculateStandardDeviation(List<BigDecimal> values, BigDecimal mean) {
        if (values == null || values.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal sumSquaredDiffs = values.stream()
            .map(v -> v.subtract(mean).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal variance = sumSquaredDiffs.divide(
            new BigDecimal(values.size()), SCALE, RoundingMode.HALF_UP);

        // Square root approximation using Newton's method
        return sqrt(variance, SCALE);
    }

    /**
     * Detect sudden volume spike (>200% month-over-month increase).
     */
    private boolean detectSuddenVolumeSpike(List<FinancialMetrics.MonthlyVolume> monthlyVolumes) {
        if (monthlyVolumes.size() < 2) {
            return false;
        }

        for (int i = 1; i < monthlyVolumes.size(); i++) {
            BigDecimal current = monthlyVolumes.get(i).getVolume();
            BigDecimal previous = monthlyVolumes.get(i - 1).getVolume();

            if (previous.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal percentChange = current.subtract(previous)
                    .divide(previous, SCALE, RoundingMode.HALF_UP)
                    .multiply(HUNDRED);

                if (percentChange.compareTo(new BigDecimal("200")) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Find the peak month.
     */
    private String findPeakMonth(List<FinancialMetrics.MonthlyVolume> monthlyVolumes) {
        return monthlyVolumes.stream()
            .max(Comparator.comparing(FinancialMetrics.MonthlyVolume::getVolume))
            .map(FinancialMetrics.MonthlyVolume::getMonth)
            .orElse("N/A");
    }

    /**
     * Find the trough month.
     */
    private String findTroughMonth(List<FinancialMetrics.MonthlyVolume> monthlyVolumes) {
        return monthlyVolumes.stream()
            .min(Comparator.comparing(FinancialMetrics.MonthlyVolume::getVolume))
            .map(FinancialMetrics.MonthlyVolume::getMonth)
            .orElse("N/A");
    }

    /**
     * Build empty metrics for no data scenario.
     */
    private FinancialMetrics buildEmptyMetrics() {
        return FinancialMetrics.builder()
            .last3MonthsVolume(BigDecimal.ZERO)
            .last6MonthsVolume(BigDecimal.ZERO)
            .last12MonthsVolume(BigDecimal.ZERO)
            .averageMonthlyVolume(BigDecimal.ZERO)
            .averageTransactionValue(BigDecimal.ZERO)
            .totalTransactionCount(0)
            .successfulTransactionCount(0)
            .failedTransactionCount(0)
            .uniqueCustomerCount(0)
            .top10CustomerVolume(BigDecimal.ZERO)
            .customerConcentration(BigDecimal.ZERO)
            .monthlyVolumes(new ArrayList<>())
            .customerVolumes(new HashMap<>())
            .consistencyScore(BigDecimal.ZERO)
            .growthRate(BigDecimal.ZERO)
            .bounceRate(BigDecimal.ZERO)
            .previousPeriodVolume(BigDecimal.ZERO)
            .isSeasonalBusiness(false)
            .coefficientOfVariation(BigDecimal.ZERO)
            .peakMonth("N/A")
            .troughMonth("N/A")
            .hasSuddenVolumeSpike(false)
            .hasLowCustomerDiversity(true)
            .hasSinglePayerDominance(false)
            .build();
    }

    /**
     * Calculate square root using Newton's method.
     */
    private BigDecimal sqrt(BigDecimal value, int scale) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal x = value;
        BigDecimal two = new BigDecimal("2");

        // Newton's method iterations
        for (int i = 0; i < 20; i++) {
            BigDecimal nextX = x.add(value.divide(x, scale + 2, RoundingMode.HALF_UP))
                .divide(two, scale + 2, RoundingMode.HALF_UP);

            if (x.subtract(nextX).abs().compareTo(new BigDecimal("0.00001")) < 0) {
                break;
            }
            x = nextX;
        }

        return x.setScale(scale, RoundingMode.HALF_UP);
    }
}
