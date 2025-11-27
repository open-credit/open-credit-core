package com.lumexpay.vortexa.credit.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumexpay.vortexa.credit.config.UpiPlatformConfig;
import com.lumexpay.vortexa.credit.dto.UpiTransaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Client for fetching UPI transaction data from the collection platform.
 * Supports mock data generation when the actual platform is unavailable.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UpiPlatformClient {

    private final UpiPlatformConfig config;
    private final ObjectMapper objectMapper;
    private final MockTransactionDataProvider mockDataProvider;

    @Value("${upi.platform.use-mock:true}")
    private boolean useMockData;

    @Value("${upi.platform.fallback-to-mock:true}")
    private boolean fallbackToMock;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Fetch transactions for a merchant within a date range.
     * Will use mock data if configured or if real platform is unavailable.
     *
     * @param merchantId Merchant identifier
     * @param startDate  Start date (inclusive)
     * @param endDate    End date (inclusive)
     * @return List of UPI transactions
     */
    public List<UpiTransaction> getTransactions(String merchantId, LocalDate startDate, LocalDate endDate) {
        log.info("Fetching transactions for merchant {} from {} to {}", merchantId, startDate, endDate);

        // If mock mode is explicitly enabled, use mock data
        if (useMockData) {
            log.info("Using mock data provider (mock mode enabled)");
            return mockDataProvider.generateTransactions(merchantId, startDate, endDate);
        }

        String url = String.format("%s/api/v1/merchants/%s/transactions?start_date=%s&end_date=%s",
                config.getBaseUrl(),
                merchantId,
                startDate.format(DATE_FORMATTER),
                endDate.format(DATE_FORMATTER));

        int attempts = 0;
        Exception lastException = null;

        while (attempts < config.getRetryAttempts()) {
            attempts++;
            try {
                List<UpiTransaction> transactions = executeRequest(url);
                if (transactions != null) {
                    return transactions;
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("Attempt {} failed for merchant {}: {}", attempts, merchantId, e.getMessage());
                if (attempts < config.getRetryAttempts()) {
                    try {
                        Thread.sleep(1000 * attempts); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // Fallback to mock data if enabled
        if (fallbackToMock) {
            log.warn("UPI platform unavailable, falling back to mock data for merchant: {}", merchantId);
            return mockDataProvider.generateTransactions(merchantId, startDate, endDate);
        }

        log.error("Failed to fetch transactions for merchant {} after {} attempts", merchantId, attempts,
                lastException);
        throw new RuntimeException("Failed to fetch transactions from UPI platform", lastException);
    }

    /**
     * Execute HTTP request to fetch transactions.
     */
    private List<UpiTransaction> executeRequest(String url) throws IOException {
         List<UpiTransaction> transactions = null;
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(config.getTimeoutSeconds(), TimeUnit.SECONDS))
                .setResponseTimeout(Timeout.of(config.getTimeoutSeconds(), TimeUnit.SECONDS))
                .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpGet request = new HttpGet(url);
            request.setHeader("Content-Type", "application/json");
            request.setHeader("X-API-Key", config.getApiKey());

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode == 200) {
                     transactions = objectMapper.readValue(
                            responseBody,
                            new TypeReference<List<UpiTransaction>>() {
                            });
                    log.info("Successfully fetched {} transactions", transactions.size());
                    return transactions;
                } else if (statusCode == 404) {
                    log.warn("No transactions found (404 response)");
                    return new ArrayList<>();
                } else {
                    throw new IOException("Unexpected response status: " + statusCode + ", body: " + responseBody);
                }
            } catch (ParseException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } 
        }
        return null;
    }

    /**
     * Fetch transactions for the last N months.
     *
     * @param merchantId Merchant identifier
     * @param months     Number of months to look back
     * @return List of UPI transactions
     */
    public List<UpiTransaction> getTransactionsForLastMonths(String merchantId, int months) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(months);
        return getTransactions(merchantId, startDate, endDate);
    }

    /**
     * Generate transactions for a specific scenario (for testing/demo).
     *
     * @param scenario   Scenario name (EXCELLENT, GOOD, POOR, GROWING, DECLINING,
     *                   etc.)
     * @param merchantId Merchant identifier
     * @param months     Number of months of data
     * @return List of UPI transactions
     */
    public List<UpiTransaction> getTransactionsForScenario(String scenario, String merchantId, int months) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(months);
        return mockDataProvider.generateScenario(scenario, merchantId, startDate, endDate);
    }

    /**
     * Check if the UPI platform is available.
     *
     * @return true if platform is accessible
     */
    public boolean isAvailable() {
        try {
            String url = config.getBaseUrl() + "/health";
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.of(5, TimeUnit.SECONDS))
                    .setResponseTimeout(Timeout.of(5, TimeUnit.SECONDS))
                    .build();

            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .build()) {

                HttpGet request = new HttpGet(url);
                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    return response.getCode() == 200;
                }
            }
        } catch (Exception e) {
            log.warn("UPI platform health check failed: {}", e.getMessage());
            return false;
        }
    }
}
