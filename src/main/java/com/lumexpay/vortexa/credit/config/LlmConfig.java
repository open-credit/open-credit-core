package com.lumexpay.vortexa.credit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for LLM/FinGPT integration.
 * Supports multiple providers: FinGPT, OpenAI, Ollama (local), Azure OpenAI
 */
@Configuration
@ConfigurationProperties(prefix = "llm")
@Data
public class LlmConfig {

    /**
     * Enable/disable LLM features
     */
    private boolean enabled = true;

    /**
     * LLM provider: FINGPT, OPENAI, OLLAMA, AZURE_OPENAI
     */
    private Provider provider = Provider.OPENAI;

    /**
     * API configuration
     */
    private ApiConfig api = new ApiConfig();

    /**
     * Model configuration
     */
    private ModelConfig model = new ModelConfig();

    /**
     * Feature toggles
     */
    private Features features = new Features();

    /**
     * Prompt templates
     */
    private Map<String, String> promptTemplates = new HashMap<>();

    public enum Provider {
        FINGPT,      // FinGPT specialized financial model
        OPENAI,      // OpenAI GPT-4/3.5
        OLLAMA,      // Local Ollama models
        AZURE_OPENAI // Azure OpenAI Service
    }

    @Data
    public static class ApiConfig {
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "";
        private int timeoutSeconds = 60;
        private int maxRetries = 3;
        private String organizationId = "";
    }

    @Data
    public static class ModelConfig {
        // Default models for different providers
        private String openaiModel = "gpt-4o";
        private String fingptModel = "fingpt-forecaster";
        private String ollamaModel = "llama3:8b";
        private String azureDeployment = "gpt-4";
        
        // Generation parameters
        private double temperature = 0.7;
        private int maxTokens = 2048;
        private double topP = 0.9;
        private double frequencyPenalty = 0.0;
        private double presencePenalty = 0.0;
    }

    @Data
    public static class Features {
        private boolean creditInsights = true;
        private boolean riskNarrative = true;
        private boolean recommendations = true;
        private boolean chatInterface = true;
        private boolean anomalyExplanation = true;
        private boolean reportEnhancement = true;
        private boolean comparativeAnalysis = true;
    }
}
