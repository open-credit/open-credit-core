package com.lumexpay.vortexa.credit.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumexpay.vortexa.credit.config.LlmConfig;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * LLM Client for integrating with various LLM providers including FinGPT.
 * Supports OpenAI, FinGPT, Ollama, and Azure OpenAI.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LlmClient {

    private final LlmConfig config;
    private final ObjectMapper objectMapper;

    /**
     * Generate a completion from the LLM.
     *
     * @param prompt User prompt
     * @param systemPrompt System/context prompt
     * @return Generated text response
     */
    public String generateCompletion(String prompt, String systemPrompt) {
        if (!config.isEnabled()) {
            log.debug("LLM is disabled, returning empty response");
            return "";
        }

        try {
            return switch (config.getProvider()) {
                case OPENAI, AZURE_OPENAI -> callOpenAiApi(prompt, systemPrompt);
                case FINGPT -> callFinGptApi(prompt, systemPrompt);
                case OLLAMA -> callOllamaApi(prompt, systemPrompt);
            };
        } catch (Exception e) {
            log.error("LLM completion failed: {}", e.getMessage(), e);
            return generateFallbackResponse(prompt);
        }
    }

    /**
     * Generate completion with conversation history for chat.
     */
    public String generateChatCompletion(List<ChatMessage> messages, String systemPrompt) {
        if (!config.isEnabled()) {
            return "LLM features are currently disabled.";
        }

        try {
            return switch (config.getProvider()) {
                case OPENAI, AZURE_OPENAI -> callOpenAiChatApi(messages, systemPrompt);
                case FINGPT -> callFinGptChatApi(messages, systemPrompt);
                case OLLAMA -> callOllamaChatApi(messages, systemPrompt);
            };
        } catch (Exception e) {
            log.error("LLM chat completion failed: {}", e.getMessage(), e);
            return "I apologize, but I'm unable to process your request at the moment. Please try again later.";
        }
    }

    /**
     * Call OpenAI-compatible API (works for OpenAI and Azure OpenAI).
     */
    private String callOpenAiApi(String prompt, String systemPrompt) throws ParseException, IOException {
        String url = config.getApi().getBaseUrl() + "/chat/completions";
        
        if (config.getProvider() == LlmConfig.Provider.AZURE_OPENAI) {
            url = config.getApi().getBaseUrl() + "/openai/deployments/" + 
                  config.getModel().getAzureDeployment() + "/chat/completions?api-version=2024-02-15-preview";
        }

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", prompt));

        Map<String, Object> requestBody = Map.of(
            "model", config.getModel().getOpenaiModel(),
            "messages", messages,
            "temperature", config.getModel().getTemperature(),
            "max_tokens", config.getModel().getMaxTokens(),
            "top_p", config.getModel().getTopP()
        );

        return executeRequest(url, requestBody, true);
    }

    /**
     * Call OpenAI Chat API with message history.
     */
    private String callOpenAiChatApi(List<ChatMessage> chatMessages, String systemPrompt) throws ParseException, IOException {
        String url = config.getApi().getBaseUrl() + "/chat/completions";

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        for (ChatMessage msg : chatMessages) {
            messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }

        Map<String, Object> requestBody = Map.of(
            "model", config.getModel().getOpenaiModel(),
            "messages", messages,
            "temperature", config.getModel().getTemperature(),
            "max_tokens", config.getModel().getMaxTokens()
        );

        return executeRequest(url, requestBody, true);
    }

    /**
     * Call FinGPT API - specialized for financial analysis.
     * FinGPT can be deployed via HuggingFace or custom endpoints.
     */
    private String callFinGptApi(String prompt, String systemPrompt) throws ParseException, IOException {
        String url = config.getApi().getBaseUrl() + "/generate";

        // FinGPT typically uses a different request format
        String fullPrompt = systemPrompt != null ? 
            systemPrompt + "\n\n" + prompt : prompt;

        Map<String, Object> requestBody = Map.of(
            "inputs", fullPrompt,
            "parameters", Map.of(
                "max_new_tokens", config.getModel().getMaxTokens(),
                "temperature", config.getModel().getTemperature(),
                "top_p", config.getModel().getTopP(),
                "do_sample", true
            )
        );

        return executeFinGptRequest(url, requestBody);
    }

    /**
     * Call FinGPT Chat API.
     */
    private String callFinGptChatApi(List<ChatMessage> messages, String systemPrompt) throws ParseException, IOException {
        // Convert chat messages to single prompt for FinGPT
        StringBuilder promptBuilder = new StringBuilder();
        if (systemPrompt != null) {
            promptBuilder.append("System: ").append(systemPrompt).append("\n\n");
        }
        for (ChatMessage msg : messages) {
            promptBuilder.append(msg.getRole().equals("user") ? "Human: " : "Assistant: ")
                .append(msg.getContent()).append("\n\n");
        }
        promptBuilder.append("Assistant: ");

        return callFinGptApi(promptBuilder.toString(), null);
    }

    /**
     * Call Ollama API for local LLM inference.
     */
    private String callOllamaApi(String prompt, String systemPrompt) throws ParseException, IOException {
        String url = config.getApi().getBaseUrl() + "/api/generate";

        String fullPrompt = systemPrompt != null ?
            "System: " + systemPrompt + "\n\nUser: " + prompt + "\n\nAssistant:" : prompt;

        Map<String, Object> requestBody = Map.of(
            "model", config.getModel().getOllamaModel(),
            "prompt", fullPrompt,
            "stream", false,
            "options", Map.of(
                "temperature", config.getModel().getTemperature(),
                "num_predict", config.getModel().getMaxTokens()
            )
        );

        return executeOllamaRequest(url, requestBody);
    }

    /**
     * Call Ollama Chat API.
     */
    private String callOllamaChatApi(List<ChatMessage> messages, String systemPrompt) throws ParseException, IOException {
        String url = config.getApi().getBaseUrl() + "/api/chat";

        List<Map<String, String>> ollamaMessages = new ArrayList<>();
        if (systemPrompt != null) {
            ollamaMessages.add(Map.of("role", "system", "content", systemPrompt));
        }
        for (ChatMessage msg : messages) {
            ollamaMessages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }

        Map<String, Object> requestBody = Map.of(
            "model", config.getModel().getOllamaModel(),
            "messages", ollamaMessages,
            "stream", false
        );

        return executeOllamaChatRequest(url, requestBody);
    }

    /**
     * Execute HTTP request to OpenAI-compatible API.
     */
    private String executeRequest(String url, Map<String, Object> requestBody, boolean isOpenAi) 
            throws ParseException, IOException {
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.of(config.getApi().getTimeoutSeconds(), TimeUnit.SECONDS))
            .setResponseTimeout(Timeout.of(config.getApi().getTimeoutSeconds(), TimeUnit.SECONDS))
            .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpPost request = new HttpPost(url);
            request.setHeader("Content-Type", "application/json");
            
            if (isOpenAi) {
                if (config.getProvider() == LlmConfig.Provider.AZURE_OPENAI) {
                    request.setHeader("api-key", config.getApi().getApiKey());
                } else {
                    request.setHeader("Authorization", "Bearer " + config.getApi().getApiKey());
                    if (config.getApi().getOrganizationId() != null && 
                        !config.getApi().getOrganizationId().isEmpty()) {
                        request.setHeader("OpenAI-Organization", config.getApi().getOrganizationId());
                    }
                }
            }

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            request.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                int statusCode = response.getCode();

                if (statusCode != 200) {
                    log.error("LLM API error: {} - {}", statusCode, responseBody);
                    throw new IOException("LLM API returned status: " + statusCode);
                }

                JsonNode jsonResponse = objectMapper.readTree(responseBody);
                return jsonResponse.path("choices").path(0).path("message").path("content").asText();
            }
        }
    }

    /**
     * Execute request to FinGPT API.
     */
    private String executeFinGptRequest(String url, Map<String, Object> requestBody) throws ParseException, IOException {
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.of(config.getApi().getTimeoutSeconds(), TimeUnit.SECONDS))
            .setResponseTimeout(Timeout.of(config.getApi().getTimeoutSeconds(), TimeUnit.SECONDS))
            .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpPost request = new HttpPost(url);
            request.setHeader("Content-Type", "application/json");
            if (config.getApi().getApiKey() != null && !config.getApi().getApiKey().isEmpty()) {
                request.setHeader("Authorization", "Bearer " + config.getApi().getApiKey());
            }

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            request.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                int statusCode = response.getCode();

                if (statusCode != 200) {
                    log.error("FinGPT API error: {} - {}", statusCode, responseBody);
                    throw new IOException("FinGPT API returned status: " + statusCode);
                }

                JsonNode jsonResponse = objectMapper.readTree(responseBody);
                // FinGPT may return in different formats
                if (jsonResponse.has("generated_text")) {
                    return jsonResponse.path("generated_text").asText();
                } else if (jsonResponse.isArray() && jsonResponse.size() > 0) {
                    return jsonResponse.get(0).path("generated_text").asText();
                }
                return responseBody;
            }
        }
    }

    /**
     * Execute request to Ollama API.
     */
    private String executeOllamaRequest(String url, Map<String, Object> requestBody) throws ParseException, IOException {
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.of(config.getApi().getTimeoutSeconds(), TimeUnit.SECONDS))
            .setResponseTimeout(Timeout.of(120, TimeUnit.SECONDS)) // Ollama may need more time
            .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpPost request = new HttpPost(url);
            request.setHeader("Content-Type", "application/json");

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            request.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String responseBody = EntityUtils.toString(response.getEntity());
               
                int statusCode = response.getCode();

                if (statusCode != 200) {
                    log.error("Ollama API error: {} - {}", statusCode, responseBody);
                    throw new IOException("Ollama API returned status: " + statusCode);
                }

                JsonNode jsonResponse = objectMapper.readTree(responseBody);
                return jsonResponse.path("response").asText();
            }
        }
    }

    /**
     * Execute Ollama chat request.
     */
    private String executeOllamaChatRequest(String url, Map<String, Object> requestBody) throws ParseException, IOException {
        RequestConfig requestConfig = RequestConfig.custom()
            .setResponseTimeout(Timeout.of(120, TimeUnit.SECONDS))
            .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpPost request = new HttpPost(url);
            request.setHeader("Content-Type", "application/json");

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            request.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                JsonNode jsonResponse = objectMapper.readTree(responseBody);
                return jsonResponse.path("message").path("content").asText();
            }
        }
    }

    /**
     * Generate fallback response when LLM is unavailable.
     */
    private String generateFallbackResponse(String prompt) {
        return "Unable to generate AI-powered insights at this time. " +
               "Please refer to the numerical metrics for assessment details.";
    }

    /**
     * Check if LLM service is available.
     */
    public boolean isAvailable() {
        if (!config.isEnabled()) {
            return false;
        }
        try {
            String testResponse = generateCompletion("Hello", "Respond with 'OK'");
            return testResponse != null && !testResponse.isEmpty();
        } catch (Exception e) {
            log.warn("LLM availability check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Chat message model.
     */
    @Data
    @Builder
    public static class ChatMessage {
        private String role; // "user", "assistant", "system"
        private String content;
    }
}
