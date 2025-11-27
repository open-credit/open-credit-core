package com.lumexpay.vortexa.credit.client;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.lumexpay.vortexa.credit.dto.UpiTransaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock transaction data provider for testing and demo purposes.
 * Generates realistic UPI transaction data when the actual UPI platform is unavailable.
 */
@Component
@Slf4j
public class MockTransactionDataProvider {

    private static final String[] PAYER_VPAS = {
        "customer1@oksbi", "customer2@okaxis", "buyer3@ybl", "shopper4@paytm",
        "client5@okicici", "user6@upi", "merchant7@oksbi", "trader8@ybl",
        "retailer9@paytm", "vendor10@okaxis", "consumer11@oksbi", "business12@okicici",
        "partner13@ybl", "supplier14@paytm", "dealer15@okaxis", "agent16@oksbi",
        "reseller17@ybl", "distributor18@okicici", "wholesaler19@paytm", "customer20@ybl"
    };

    private static final String[] MERCHANT_CATEGORIES = {
        "RETAIL", "FOOD", "SERVICES", "GROCERY", "ELECTRONICS", 
        "FASHION", "PHARMACY", "FUEL", "RESTAURANT", "GENERAL"
    };

    private static final Map<String, MerchantProfile> MERCHANT_PROFILES = new HashMap<>();

    static {
        // Define different merchant profiles for realistic data generation
        MERCHANT_PROFILES.put("LOW_RISK", new MerchantProfile(
            300000, 600000, 0.03, 0.05, 0.10, 20, 50
        ));
        MERCHANT_PROFILES.put("MEDIUM_RISK", new MerchantProfile(
            100000, 300000, 0.08, -0.05, 0.30, 10, 30
        ));
        MERCHANT_PROFILES.put("HIGH_RISK", new MerchantProfile(
            25000, 100000, 0.18, -0.15, 0.50, 5, 15
        ));
        MERCHANT_PROFILES.put("GROWING", new MerchantProfile(
            150000, 400000, 0.04, 0.25, 0.20, 15, 40
        ));
        MERCHANT_PROFILES.put("SEASONAL", new MerchantProfile(
            100000, 500000, 0.05, 0.10, 0.25, 10, 35
        ));
        MERCHANT_PROFILES.put("NEW_BUSINESS", new MerchantProfile(
            30000, 80000, 0.12, 0.40, 0.35, 3, 10
        ));
    }

    /**
     * Generate mock transactions for a merchant.
     *
     * @param merchantId Merchant identifier
     * @param startDate  Start date
     * @param endDate    End date
     * @return List of mock transactions
     */
    public List<UpiTransaction> generateTransactions(String merchantId, LocalDate startDate, LocalDate endDate) {
        log.info("Generating mock transactions for merchant: {} from {} to {}", merchantId, startDate, endDate);
        
        // Determine merchant profile based on merchantId pattern
        MerchantProfile profile = determineMerchantProfile(merchantId);
        
        List<UpiTransaction> transactions = new ArrayList<>();
        Random random = new Random(merchantId.hashCode()); // Seed for reproducibility
        
        LocalDate current = startDate;
        int transactionCounter = 0;
        BigDecimal baseMonthlyVolume = new BigDecimal(profile.minMonthlyVolume + 
            random.nextInt(profile.maxMonthlyVolume - profile.minMonthlyVolume));
        
        while (!current.isAfter(endDate)) {
            // Calculate how many transactions for this day
            int dailyTransactions = profile.minDailyTransactions + 
                random.nextInt(profile.maxDailyTransactions - profile.minDailyTransactions + 1);
            
            // Apply day-of-week variation (weekends may be busier for retail)
            int dayOfWeek = current.getDayOfWeek().getValue();
            if (dayOfWeek >= 6) { // Weekend
                dailyTransactions = (int) (dailyTransactions * 1.3);
            }
            
            // Apply monthly growth/decline
            int monthsFromStart = (int) java.time.temporal.ChronoUnit.MONTHS.between(startDate, current);
            double growthFactor = Math.pow(1 + profile.monthlyGrowthRate, monthsFromStart);
            
            // Apply seasonality for seasonal merchants
            double seasonalFactor = 1.0;
            if (merchantId.toUpperCase().contains("SEASONAL") || 
                MERCHANT_PROFILES.get("SEASONAL").equals(profile)) {
                int month = current.getMonthValue();
                // Peak in Oct-Dec (festival season)
                if (month >= 10 && month <= 12) {
                    seasonalFactor = 1.8;
                } else if (month >= 1 && month <= 3) {
                    seasonalFactor = 0.7;
                }
            }
            
            for (int i = 0; i < dailyTransactions; i++) {
                transactionCounter++;
                
                // Determine if this transaction fails
                boolean isFailed = random.nextDouble() < profile.bounceRate;
                
                // Generate transaction amount with variation
                BigDecimal avgTransactionAmount = baseMonthlyVolume
                    .multiply(BigDecimal.valueOf(growthFactor))
                    .multiply(BigDecimal.valueOf(seasonalFactor))
                    .divide(new BigDecimal(30 * profile.maxDailyTransactions), 2, RoundingMode.HALF_UP);
                
                // Add random variation (-50% to +100%)
                double variation = 0.5 + random.nextDouble() * 1.5;
                BigDecimal amount = avgTransactionAmount.multiply(BigDecimal.valueOf(variation))
                    .setScale(2, RoundingMode.HALF_UP);
                
                // Minimum amount
                if (amount.compareTo(new BigDecimal("10")) < 0) {
                    amount = new BigDecimal("10").add(new BigDecimal(random.nextInt(100)));
                }
                
                // Generate random time during business hours
                int hour = 8 + random.nextInt(14); // 8 AM to 10 PM
                int minute = random.nextInt(60);
                LocalDateTime transactionTime = LocalDateTime.of(current, LocalTime.of(hour, minute));
                
                // Select payer (with some repeat customers based on concentration)
                String payerVpa = selectPayer(random, profile.customerConcentration);
                
                UpiTransaction transaction = UpiTransaction.builder()
                    .transactionId(String.format("TXN_%s_%06d", merchantId, transactionCounter))
                    .merchantId(merchantId)
                    .transactionDate(transactionTime)
                    .amount(amount)
                    .payerVpa(payerVpa)
                    .transactionType(UpiTransaction.TransactionType.CREDIT)
                    .status(isFailed ? UpiTransaction.TransactionStatus.FAILED : 
                                       UpiTransaction.TransactionStatus.SUCCESS)
                    .merchantCategory(MERCHANT_CATEGORIES[random.nextInt(MERCHANT_CATEGORIES.length)])
                    .build();
                
                transactions.add(transaction);
            }
            
            current = current.plusDays(1);
        }
        
        log.info("Generated {} mock transactions for merchant: {}", transactions.size(), merchantId);
        return transactions;
    }

    /**
     * Determine merchant profile based on merchant ID patterns.
     */
    private MerchantProfile determineMerchantProfile(String merchantId) {
        String upper = merchantId.toUpperCase();
        
        if (upper.contains("LOW") || upper.contains("PREMIUM") || upper.contains("GOLD")) {
            return MERCHANT_PROFILES.get("LOW_RISK");
        } else if (upper.contains("HIGH") || upper.contains("RISKY") || upper.contains("NEW")) {
            return MERCHANT_PROFILES.get("HIGH_RISK");
        } else if (upper.contains("GROW") || upper.contains("RISING")) {
            return MERCHANT_PROFILES.get("GROWING");
        } else if (upper.contains("SEASONAL") || upper.contains("FESTIVAL")) {
            return MERCHANT_PROFILES.get("SEASONAL");
        } else if (upper.contains("STARTUP") || upper.contains("FRESH")) {
            return MERCHANT_PROFILES.get("NEW_BUSINESS");
        }
        
        // Default based on hash for reproducibility
        int hash = Math.abs(merchantId.hashCode());
        String[] profiles = {"LOW_RISK", "MEDIUM_RISK", "MEDIUM_RISK", "GROWING", "SEASONAL"};
        return MERCHANT_PROFILES.get(profiles[hash % profiles.length]);
    }

    /**
     * Select a payer VPA with customer concentration logic.
     */
    private String selectPayer(Random random, double concentration) {
        // Higher concentration means more transactions from top customers
        if (random.nextDouble() < concentration) {
            // Select from top 5 customers
            return PAYER_VPAS[random.nextInt(5)];
        } else {
            // Select from all customers
            return PAYER_VPAS[random.nextInt(PAYER_VPAS.length)];
        }
    }

    /**
     * Merchant profile configuration.
     */
    private static class MerchantProfile {
        final int minMonthlyVolume;
        final int maxMonthlyVolume;
        final double bounceRate;
        final double monthlyGrowthRate;
        final double customerConcentration;
        final int minDailyTransactions;
        final int maxDailyTransactions;

        MerchantProfile(int minMonthlyVolume, int maxMonthlyVolume, double bounceRate,
                       double monthlyGrowthRate, double customerConcentration,
                       int minDailyTransactions, int maxDailyTransactions) {
            this.minMonthlyVolume = minMonthlyVolume;
            this.maxMonthlyVolume = maxMonthlyVolume;
            this.bounceRate = bounceRate;
            this.monthlyGrowthRate = monthlyGrowthRate;
            this.customerConcentration = customerConcentration;
            this.minDailyTransactions = minDailyTransactions;
            this.maxDailyTransactions = maxDailyTransactions;
        }
    }

    /**
     * Generate a specific merchant scenario for demo purposes.
     */
    public List<UpiTransaction> generateScenario(String scenario, String merchantId, 
                                                  LocalDate startDate, LocalDate endDate) {
        log.info("Generating {} scenario for merchant: {}", scenario, merchantId);
        
        // Override profile based on scenario
        MerchantProfile profile = switch (scenario.toUpperCase()) {
            case "EXCELLENT" -> MERCHANT_PROFILES.get("LOW_RISK");
            case "GOOD" -> MERCHANT_PROFILES.get("MEDIUM_RISK");
            case "POOR" -> MERCHANT_PROFILES.get("HIGH_RISK");
            case "GROWING" -> MERCHANT_PROFILES.get("GROWING");
            case "DECLINING" -> new MerchantProfile(150000, 300000, 0.10, -0.08, 0.30, 10, 25);
            case "SEASONAL" -> MERCHANT_PROFILES.get("SEASONAL");
            case "STARTUP" -> MERCHANT_PROFILES.get("NEW_BUSINESS");
            case "INELIGIBLE" -> new MerchantProfile(10000, 20000, 0.25, -0.20, 0.70, 1, 3);
            default -> determineMerchantProfile(merchantId);
        };
        
        return generateTransactionsWithProfile(merchantId, startDate, endDate, profile);
    }

    private List<UpiTransaction> generateTransactionsWithProfile(String merchantId, 
            LocalDate startDate, LocalDate endDate, MerchantProfile profile) {
        List<UpiTransaction> transactions = new ArrayList<>();
        Random random = new Random(merchantId.hashCode());
        
        LocalDate current = startDate;
        int transactionCounter = 0;
        BigDecimal baseMonthlyVolume = new BigDecimal(profile.minMonthlyVolume + 
            random.nextInt(profile.maxMonthlyVolume - profile.minMonthlyVolume));
        
        while (!current.isAfter(endDate)) {
            int dailyTransactions = profile.minDailyTransactions + 
                random.nextInt(Math.max(1, profile.maxDailyTransactions - profile.minDailyTransactions + 1));
            
            int monthsFromStart = (int) java.time.temporal.ChronoUnit.MONTHS.between(startDate, current);
            double growthFactor = Math.pow(1 + profile.monthlyGrowthRate, monthsFromStart);
            
            for (int i = 0; i < dailyTransactions; i++) {
                transactionCounter++;
                boolean isFailed = random.nextDouble() < profile.bounceRate;
                
                BigDecimal avgTransactionAmount = baseMonthlyVolume
                    .multiply(BigDecimal.valueOf(growthFactor))
                    .divide(new BigDecimal(30 * Math.max(1, profile.maxDailyTransactions)), 2, RoundingMode.HALF_UP);
                
                double variation = 0.5 + random.nextDouble() * 1.5;
                BigDecimal amount = avgTransactionAmount.multiply(BigDecimal.valueOf(variation))
                    .setScale(2, RoundingMode.HALF_UP);
                
                if (amount.compareTo(new BigDecimal("10")) < 0) {
                    amount = new BigDecimal("10").add(new BigDecimal(random.nextInt(100)));
                }
                
                int hour = 8 + random.nextInt(14);
                int minute = random.nextInt(60);
                LocalDateTime transactionTime = LocalDateTime.of(current, LocalTime.of(hour, minute));
                
                String payerVpa = selectPayer(random, profile.customerConcentration);
                
                transactions.add(UpiTransaction.builder()
                    .transactionId(String.format("TXN_%s_%06d", merchantId, transactionCounter))
                    .merchantId(merchantId)
                    .transactionDate(transactionTime)
                    .amount(amount)
                    .payerVpa(payerVpa)
                    .transactionType(UpiTransaction.TransactionType.CREDIT)
                    .status(isFailed ? UpiTransaction.TransactionStatus.FAILED : 
                                       UpiTransaction.TransactionStatus.SUCCESS)
                    .merchantCategory(MERCHANT_CATEGORIES[random.nextInt(MERCHANT_CATEGORIES.length)])
                    .build());
            }
            
            current = current.plusDays(1);
        }
        
        return transactions;
    }
}
