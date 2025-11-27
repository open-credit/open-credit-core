package com.lumexpay.vortexa.credit.controller;

import com.lumexpay.vortexa.credit.llm.training.CreditScoringTrainingPipeline;
import com.lumexpay.vortexa.credit.llm.training.CreditScoringTrainingPipeline.TrainingStats;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/**
 * API endpoints for LLM training data generation.
 * 
 * These endpoints help you generate training data for fine-tuning
 * LLMs to explain credit scoring decisions.
 */
@RestController
@RequestMapping("/api/v1/training")
@RequiredArgsConstructor
@Slf4j
public class TrainingController {

    private final CreditScoringTrainingPipeline trainingPipeline;

    /**
     * Generate training data for LLM fine-tuning.
     * 
     * This creates training examples that teach the LLM to:
     * - Explain credit scores in simple terms
     * - Provide personalized recommendations
     * - Answer questions about the scoring system
     * - Generate risk analyses for lenders
     * 
     * The LLM learns to EXPLAIN decisions, not MAKE them.
     * All scoring decisions come from the rule engine.
     */
    @PostMapping("/generate")
    public ResponseEntity<TrainingStats> generateTrainingData(
            @RequestBody TrainingRequest request) throws IOException {
        
        log.info("Generating training data: {} profiles to {}", 
            request.getNumProfiles(), request.getOutputDir());
        
        TrainingStats stats = trainingPipeline.generateCompleteTrainingData(
            request.getOutputDir(),
            request.getNumProfiles()
        );
        
        log.info("Training data generation complete: {}", stats);
        return ResponseEntity.ok(stats);
    }

    /**
     * Get recommended training configuration.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getTrainingConfig() {
        return ResponseEntity.ok(Map.of(
            "recommendedProfiles", 5000,
            "estimatedExamples", 40000,
            "estimatedTokens", 2000000,
            "supportedFormats", Map.of(
                "openai", "For OpenAI fine-tuning API",
                "alpaca", "For local fine-tuning with Alpaca-style models",
                "llama", "For Llama-2 fine-tuning",
                "huggingface", "For HuggingFace Transformers"
            ),
            "trainingCategories", Map.of(
                "score_explanation", "25% - Explain scores simply",
                "score_reasoning", "15% - Detailed breakdowns",
                "eligibility", "15% - Loan eligibility",
                "recommendations", "15% - Improvement advice",
                "risk_analysis", "10% - Lender-focused analysis",
                "breakdown", "10% - Component analysis",
                "faq", "5% - Common questions",
                "edge_cases", "5% - Special situations"
            ),
            "fineTuningOptions", Map.of(
                "openai", Map.of(
                    "cost", "$15-30 for training",
                    "perQuery", "$0.003/1K tokens",
                    "privacy", "Data goes to OpenAI",
                    "quality", "Excellent"
                ),
                "fingpt", Map.of(
                    "cost", "~$50 GPU time",
                    "perQuery", "Free (local)",
                    "privacy", "Full privacy",
                    "quality", "Very good (financial domain)"
                ),
                "ollama", Map.of(
                    "cost", "Free",
                    "perQuery", "Free (local)",
                    "privacy", "Full privacy",
                    "quality", "Good"
                )
            )
        ));
    }

    /**
     * Generate a sample training example (for preview).
     */
    @GetMapping("/sample")
    public ResponseEntity<Map<String, Object>> getSampleExample() {
        return ResponseEntity.ok(Map.of(
            "format", "OpenAI",
            "example", Map.of(
                "messages", new Object[]{
                    Map.of("role", "system", "content", 
                        "You are a credit advisor AI for OpenCredit..."),
                    Map.of("role", "user", "content",
                        "Explain this merchant's credit score.\n\n" +
                        "Merchant Profile:\n" +
                        "- Credit Score: 72/100 (MEDIUM Risk)\n" +
                        "- Monthly Volume: ₹1.5L\n" +
                        "- Bounce Rate: 8%"),
                    Map.of("role", "assistant", "content",
                        "Your credit score of 72 is good, placing you in the MEDIUM risk category...\n" +
                        "**What's working well:**\n✓ Healthy transaction volume...\n" +
                        "**Areas for improvement:**\n• Bounce rate of 8%...")
                }
            ),
            "note", "The LLM learns to EXPLAIN scores calculated by the rule engine, not to calculate them."
        ));
    }

    @Data
    public static class TrainingRequest {
        private String outputDir = "/tmp/training-data";
        private int numProfiles = 1000;
    }
}
