package com.lumexpay.vortexa.credit.controller;

import com.lumexpay.vortexa.credit.service.CreditChatService;
import com.lumexpay.vortexa.credit.service.CreditChatService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API controller for AI-powered chat interface.
 * Allows merchants and lenders to interact with credit assessments conversationally.
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Chat", description = "Conversational AI interface for credit assessment Q&A")
public class ChatController {

    private final CreditChatService chatService;

    /**
     * Start a new chat session.
     */
    @PostMapping("/session/start")
    @Operation(
        summary = "Start chat session",
        description = "Initialize a new chat session for a merchant. Returns session ID for subsequent messages."
    )
    public ResponseEntity<?> startSession(@RequestBody StartSessionRequest request) {
        log.info("Starting chat session for merchant: {} as {}", request.getMerchantId(), request.getUserType());
        
        try {
            ChatUserType userType = ChatUserType.valueOf(request.getUserType().toUpperCase());
            ChatSession session = chatService.startSession(request.getMerchantId(), userType);
            
            String welcomeMessage = userType == ChatUserType.MERCHANT ?
                "Hello! I'm your credit advisor. I have access to your credit assessment and can help you " +
                "understand your credit score, loan eligibility, and how to improve your profile. " +
                "What would you like to know?" :
                "Welcome. I have loaded the credit assessment for merchant " + request.getMerchantId() + ". " +
                "I can help you analyze the risk profile, understand the metrics, and provide lending recommendations. " +
                "What would you like to analyze?";
            
            return ResponseEntity.ok(Map.of(
                "sessionId", session.getSessionId(),
                "merchantId", session.getMerchantId(),
                "hasAssessment", session.isHasAssessment(),
                "message", welcomeMessage,
                "suggestedQuestions", getSuggestedQuestions(userType)
            ));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid user type",
                "validTypes", List.of("MERCHANT", "LENDER")
            ));
        }
    }

    /**
     * Send a chat message.
     */
    @PostMapping("/message")
    @Operation(
        summary = "Send message",
        description = "Send a message in an existing chat session and receive AI response"
    )
    public ResponseEntity<?> sendMessage(@RequestBody ChatMessageRequest request) {
        log.debug("Chat message in session {}: {}", request.getSessionId(), 
            request.getMessage().substring(0, Math.min(50, request.getMessage().length())));
        
        ChatResponse response = chatService.chat(request.getSessionId(), request.getMessage());
        
        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get quick answer without full chat session.
     */
    @GetMapping("/quick/{merchantId}/{question}")
    @Operation(
        summary = "Quick answer",
        description = "Get instant answers to common questions without starting a full chat session"
    )
    public ResponseEntity<?> quickAnswer(
            @Parameter(description = "Merchant ID") @PathVariable String merchantId,
            @Parameter(description = "Question type") @PathVariable String question) {
        
        try {
            QuickQuestion q = QuickQuestion.valueOf(question.toUpperCase());
            ChatResponse response = chatService.quickAnswer(merchantId, q);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid question type",
                "validQuestions", List.of(
                    "CREDIT_SCORE - What is my credit score?",
                    "LOAN_ELIGIBILITY - Am I eligible for a loan?",
                    "WHY_THIS_SCORE - Why did I get this score?",
                    "HOW_TO_IMPROVE - How can I improve?",
                    "RISK_CATEGORY - What is my risk category?",
                    "NEXT_STEPS - What are the next steps?"
                )
            ));
        }
    }

    /**
     * Get available quick questions.
     */
    @GetMapping("/questions")
    @Operation(summary = "List quick questions", description = "Get list of available quick question types")
    public ResponseEntity<?> getQuickQuestions() {
        return ResponseEntity.ok(Map.of(
            "questions", List.of(
                Map.of("id", "CREDIT_SCORE", "label", "What is my credit score?"),
                Map.of("id", "LOAN_ELIGIBILITY", "label", "Am I eligible for a loan?"),
                Map.of("id", "WHY_THIS_SCORE", "label", "Why did I get this score?"),
                Map.of("id", "HOW_TO_IMPROVE", "label", "How can I improve my score?"),
                Map.of("id", "RISK_CATEGORY", "label", "What does my risk category mean?"),
                Map.of("id", "NEXT_STEPS", "label", "What are the next steps?")
            )
        ));
    }

    /**
     * End a chat session.
     */
    @DeleteMapping("/session/{sessionId}")
    @Operation(summary = "End session", description = "End a chat session and clear conversation history")
    public ResponseEntity<?> endSession(@PathVariable String sessionId) {
        chatService.endSession(sessionId);
        return ResponseEntity.ok(Map.of(
            "message", "Session ended successfully",
            "sessionId", sessionId
        ));
    }

    /**
     * Get session info.
     */
    @GetMapping("/session/{sessionId}")
    @Operation(summary = "Get session info", description = "Get information about an active chat session")
    public ResponseEntity<?> getSession(@PathVariable String sessionId) {
        return chatService.getSession(sessionId)
            .map(session -> ResponseEntity.ok(Map.of(
                "sessionId", session.getSessionId(),
                "merchantId", session.getMerchantId(),
                "userType", session.getUserType().name(),
                "messageCount", session.getMessages().size(),
                "createdAt", session.getCreatedAt().toString()
            )))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Get suggested questions based on user type.
     */
    private List<String> getSuggestedQuestions(ChatUserType userType) {
        if (userType == ChatUserType.MERCHANT) {
            return List.of(
                "What is my credit score?",
                "Am I eligible for a loan?",
                "How can I improve my credit score?",
                "Why is my score not higher?",
                "What affects my loan amount?",
                "How do I reduce my bounce rate?"
            );
        } else {
            return List.of(
                "What are the key risk factors?",
                "Is this merchant a good lending candidate?",
                "What is the recommended loan structure?",
                "Are there any red flags?",
                "How does this merchant compare to industry average?",
                "What collateral or terms should we require?"
            );
        }
    }

    // ==========================================
    // REQUEST DTOs
    // ==========================================

    @Data
    public static class StartSessionRequest {
        private String merchantId;
        private String userType; // MERCHANT or LENDER
    }

    @Data
    public static class ChatMessageRequest {
        private String sessionId;
        private String message;
    }
}
