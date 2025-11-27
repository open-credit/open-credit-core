package com.lumexpay.vortexa.credit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for UPI Platform integration.
 */
@Configuration
@ConfigurationProperties(prefix = "upi.platform")
@Data
public class UpiPlatformConfig {
    private String baseUrl = "http://localhost:8081";
    private String apiKey = "";
    private int timeoutSeconds = 30;
    private int retryAttempts = 3;
}
