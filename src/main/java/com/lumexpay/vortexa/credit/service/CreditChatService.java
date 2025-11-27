package com.lumexpay.vortexa.credit.service;

import com.lumexpay.vortexa.credit.client.LlmClient;
import com.lumexpay.vortexa.credit.config.LlmConfig;
import com.lumexpay.vortexa.credit.dto.CreditAssessmentResponse;
import com.lumexpay.vortexa.credit.model.CreditAssessment;
import com.lumexpay.vortexa.credit.repository.CreditAssessmentRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat service for conversational Q&A about credit assessments.
 * Allows merchants and lenders to ask questions about credit reports.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CreditChatService {

    private final LlmClient llmClient;
    private final LlmConfig llmConfig;
    private final CreditAssessmentRepository assessmentRepository;

    // Session storage for conversation history
    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

    private static final String MERCHANT_SYSTEM_PROMPT = """
        You are a helpful credit advisor AI assistant for Indian merchants. You help small business 
        owners understand their credit assessment results and how to improve their creditworthiness.
        
        Guidelines:
        - Use simple, clear language (avoid jargon)
        - Be encouraging but honest
        - Provide specific, actionable advice
        - Reference the merchant's actual data when possible
        - Use Indian Rupee (₹) for currency
        - Consider Indian business context
        - If you don't know something, say so clearly
        
        You have access to the merchant's credit assessment data provided in the context.
        """;

    private static final String LENDER_SYSTEM_PROMPT = """
        You are a financial analysis AI assistant for lenders evaluating merchant loan applications.
        You provide detailed risk analysis and lending recommendations.
        
        Guidelines:
        - Be precise and data-driven
        - Highlight key risk factors and mitigants
        - Provide clear lending recommendations
        - Reference specific metrics from the assessment
        - Consider regulatory requirements (RBI guidelines)
        - Flag any concerns or red flags
        
        You have access to the merchant's credit assessment data provided in the context.
        """;

    /**
     * Start a new chat session.
     */
    public ChatSession startSession(String merchantId, ChatUserType userType) {
        String sessionId = UUID.randomUUID().toString();
        
        // Fetch merchant's credit assessment
        Optional<CreditAssessment> assessmentOpt = assessmentRepository
            .findTopByMerchantIdOrderByAssessmentDateDesc(merchantId);

        String context = assessmentOpt.map(this::buildAssessmentContext)
            .orElse("No credit assessment found for merchant: " + merchantId);

        ChatSession session = ChatSession.builder()
            .sessionId(sessionId)
            .merchantId(merchantId)
            .userType(userType)
            .context(context)
            .hasAssessment(assessmentOpt.isPresent())
            .messages(new ArrayList<>())
            .createdAt(LocalDateTime.now())
            .build();

        sessions.put(sessionId, session);
        log.info("Started chat session {} for merchant {} as {}", sessionId, merchantId, userType);

        return session;
    }

    /**
     * Send a message and get a response.
     */
    public ChatResponse chat(String sessionId, String userMessage) {
        ChatSession session = sessions.get(sessionId);
        if (session == null) {
            return ChatResponse.builder()
                .success(false)
                .message("Session not found. Please start a new session.")
                .build();
        }

        if (!llmConfig.isEnabled() || !llmConfig.getFeatures().isChatInterface()) {
            return ChatResponse.builder()
                .success(false)
                .message("Chat feature is currently disabled.")
                .build();
        }

        try {
            // Add user message to history
            session.getMessages().add(LlmClient.ChatMessage.builder()
                .role("user")
                .content(userMessage)
                .build());

            // Build system prompt with context
            String systemPrompt = buildSystemPrompt(session);

            // Get LLM response
            String response = llmClient.generateChatCompletion(session.getMessages(), systemPrompt);

            // Add assistant response to history
            session.getMessages().add(LlmClient.ChatMessage.builder()
                .role("assistant")
                .content(response)
                .build());

            // Keep conversation history manageable (last 20 messages)
            if (session.getMessages().size() > 20) {
                session.setMessages(new ArrayList<>(
                    session.getMessages().subList(session.getMessages().size() - 20, session.getMessages().size())
                ));
            }

            return ChatResponse.builder()
                .success(true)
                .sessionId(sessionId)
                .message(response)
                .timestamp(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Chat error for session {}: {}", sessionId, e.getMessage(), e);
            return ChatResponse.builder()
                .success(false)
                .message("I apologize, but I encountered an error processing your request. Please try again.")
                .build();
        }
    }

    /**
     * Get quick answers for common questions.
     */
    public ChatResponse quickAnswer(String merchantId, QuickQuestion question) {
        Optional<CreditAssessment> assessmentOpt = assessmentRepository
            .findTopByMerchantIdOrderByAssessmentDateDesc(merchantId);

        if (assessmentOpt.isEmpty()) {
            return ChatResponse.builder()
                .success(false)
                .message("No credit assessment found. Please run an assessment first.")
                .build();
        }

        CreditAssessment assessment = assessmentOpt.get();
        String answer = generateQuickAnswer(question, assessment);

        return ChatResponse.builder()
            .success(true)
            .message(answer)
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * End a chat session.
     */
    public void endSession(String sessionId) {
        sessions.remove(sessionId);
        log.info("Ended chat session: {}", sessionId);
    }

    /**
     * Get session info.
     */
    public Optional<ChatSession> getSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * Build system prompt with context.
     */
    private String buildSystemPrompt(ChatSession session) {
        String basePrompt = session.getUserType() == ChatUserType.MERCHANT ? 
            MERCHANT_SYSTEM_PROMPT : LENDER_SYSTEM_PROMPT;

        return basePrompt + "\n\n**MERCHANT CREDIT ASSESSMENT DATA:**\n" + session.getContext();
    }

    /**
     * Build context from credit assessment.
     */
    private String buildAssessmentContext(CreditAssessment assessment) {
        return String.format("""
            Merchant ID: %s
            Assessment Date: %s
            
            CREDIT SCORE: %d/100
            RISK CATEGORY: %s
            
            ELIGIBILITY:
            - Eligible for Loan: %s
            - Eligible Amount: ₹%s
            - Maximum Tenure: %d days
            - Interest Rate: %.1f%% p.a.
            %s
            
            FINANCIAL METRICS:
            - Last 3 Months Volume: ₹%s
            - Last 6 Months Volume: ₹%s
            - Average Monthly Volume: ₹%s
            - Total Transactions: %d
            - Unique Customers: %d
            - Average Transaction Value: ₹%s
            
            PERFORMANCE METRICS:
            - Consistency Score: %.1f/100
            - Growth Rate: %.1f%%
            - Bounce Rate: %.1f%%
            - Customer Concentration: %.1f%%
            
            SCORE BREAKDOWN:
            - Volume Score: %.0f (weight: 30%%)
            - Consistency Score: %.0f (weight: 25%%)
            - Growth Score: %.0f (weight: 15%%)
            - Bounce Rate Score: %.0f (weight: 15%%)
            - Concentration Score: %.0f (weight: 15%%)
            
            WARNINGS: %s
            STRENGTHS: %s
            """,
            assessment.getMerchantId(),
            assessment.getAssessmentDate(),
            assessment.getCreditScore(),
            assessment.getRiskCategory().getDescription(),
            assessment.getIsEligible() ? "Yes" : "No",
            formatCurrency(assessment.getEligibleLoanAmount()),
            assessment.getMaxTenureDays(),
            assessment.getRecommendedInterestRate().doubleValue(),
            assessment.getIsEligible() ? "" : "Reason: " + assessment.getIneligibilityReason(),
            formatCurrency(assessment.getLast3MonthsVolume()),
            formatCurrency(assessment.getLast6MonthsVolume()),
            formatCurrency(assessment.getAverageMonthlyVolume()),
            assessment.getTransactionCount(),
            assessment.getUniqueCustomerCount(),
            formatCurrency(assessment.getAverageTransactionValue()),
            assessment.getConsistencyScore().doubleValue(),
            assessment.getGrowthRate().doubleValue(),
            assessment.getBounceRate().doubleValue(),
            assessment.getCustomerConcentration().doubleValue(),
            assessment.getVolumeScore().doubleValue(),
            assessment.getConsistencyScore().doubleValue(),
            assessment.getGrowthScore().doubleValue(),
            assessment.getBounceRateScore().doubleValue(),
            assessment.getConcentrationScore().doubleValue(),
            assessment.getWarnings().isEmpty() ? "None" : String.join(", ", assessment.getWarnings()),
            assessment.getStrengths().isEmpty() ? "None" : String.join(", ", assessment.getStrengths())
        );
    }

    /**
     * Generate quick answer without full LLM call.
     */
    private String generateQuickAnswer(QuickQuestion question, CreditAssessment assessment) {
        return switch (question) {
            case CREDIT_SCORE -> String.format(
                "Your credit score is %d out of 100, which places you in the %s risk category. %s",
                assessment.getCreditScore(),
                assessment.getRiskCategory().name(),
                assessment.getCreditScore() >= 80 ? "This is an excellent score!" :
                assessment.getCreditScore() >= 60 ? "This is a good score with room for improvement." :
                "There are opportunities to improve your score."
            );
            
            case LOAN_ELIGIBILITY -> assessment.getIsEligible() ?
                String.format(
                    "Yes! You are eligible for a loan of up to ₹%s with a maximum tenure of %d days " +
                    "at an interest rate of %.1f%% per annum.",
                    formatCurrency(assessment.getEligibleLoanAmount()),
                    assessment.getMaxTenureDays(),
                    assessment.getRecommendedInterestRate().doubleValue()
                ) :
                String.format(
                    "Currently, you are not eligible for a loan. Reason: %s. " +
                    "I can help you understand how to become eligible.",
                    assessment.getIneligibilityReason()
                );
            
            case WHY_THIS_SCORE -> String.format(
                "Your score of %d is calculated based on 5 factors:\n" +
                "• Transaction Volume (30%%): %.0f points\n" +
                "• Business Consistency (25%%): %.0f points\n" +
                "• Growth Trend (15%%): %.0f points\n" +
                "• Payment Success Rate (15%%): %.0f points\n" +
                "• Customer Diversity (15%%): %.0f points",
                assessment.getCreditScore(),
                assessment.getVolumeScore().doubleValue(),
                assessment.getConsistencyScore().doubleValue(),
                assessment.getGrowthScore().doubleValue(),
                assessment.getBounceRateScore().doubleValue(),
                assessment.getConcentrationScore().doubleValue()
            );
            
            case HOW_TO_IMPROVE -> {
                List<String> tips = new ArrayList<>();
                if (assessment.getBounceRate().compareTo(new BigDecimal("5")) > 0) {
                    tips.add("Reduce failed transactions (current: " + assessment.getBounceRate() + "%)");
                }
                if (assessment.getCustomerConcentration().compareTo(new BigDecimal("30")) > 0) {
                    tips.add("Diversify your customer base (current concentration: " + assessment.getCustomerConcentration() + "%)");
                }
                if (assessment.getGrowthRate().compareTo(BigDecimal.ZERO) < 0) {
                    tips.add("Focus on increasing transaction volume");
                }
                if (assessment.getConsistencyScore().compareTo(new BigDecimal("70")) < 0) {
                    tips.add("Maintain more consistent monthly volumes");
                }
                yield tips.isEmpty() ? 
                    "Your profile is already strong! Continue maintaining your current business practices." :
                    "To improve your score:\n• " + String.join("\n• ", tips);
            }
            
            case RISK_CATEGORY -> String.format(
                "You are in the %s category.\n\n" +
                "%s\n\n" +
                "This affects your loan terms: %s",
                assessment.getRiskCategory().name(),
                assessment.getRiskCategory().getDescription(),
                switch (assessment.getRiskCategory()) {
                    case LOW -> "You qualify for the best rates and longest tenure.";
                    case MEDIUM -> "Standard rates apply. Improving your score can unlock better terms.";
                    case HIGH -> "Higher rates and shorter tenure apply. Focus on improving key metrics.";
                }
            );
            
            case NEXT_STEPS -> assessment.getIsEligible() ?
                "Next steps to get your loan:\n" +
                "1. Review your eligible amount: ₹" + formatCurrency(assessment.getEligibleLoanAmount()) + "\n" +
                "2. Choose your preferred tenure (up to " + assessment.getMaxTenureDays() + " days)\n" +
                "3. Apply through our lending marketplace\n" +
                "4. Complete KYC verification\n" +
                "5. Receive funds in your account" :
                "To become eligible:\n" +
                "1. Address the issue: " + assessment.getIneligibilityReason() + "\n" +
                "2. Continue accepting UPI payments to build history\n" +
                "3. Request a re-assessment after 30 days\n" +
                "4. Ask me for specific tips to improve!";
        };
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0";
        double value = amount.doubleValue();
        if (value >= 100000) {
            return String.format("%.2f L", value / 100000);
        }
        return String.format("%.0f", value);
    }

    /**
     * Chat session model.
     */
    @Data
    @Builder
    public static class ChatSession {
        private String sessionId;
        private String merchantId;
        private ChatUserType userType;
        private String context;
        private boolean hasAssessment;
        private List<LlmClient.ChatMessage> messages;
        private LocalDateTime createdAt;
    }

    /**
     * Chat response model.
     */
    @Data
    @Builder
    public static class ChatResponse {
        private boolean success;
        private String sessionId;
        private String message;
        private LocalDateTime timestamp;
        private List<String> suggestedQuestions;
    }

    /**
     * User type enum.
     */
    public enum ChatUserType {
        MERCHANT,  // Business owner asking about their credit
        LENDER     // Lender evaluating the application
    }

    /**
     * Quick question types.
     */
    public enum QuickQuestion {
        CREDIT_SCORE,
        LOAN_ELIGIBILITY,
        WHY_THIS_SCORE,
        HOW_TO_IMPROVE,
        RISK_CATEGORY,
        NEXT_STEPS
    }
}
