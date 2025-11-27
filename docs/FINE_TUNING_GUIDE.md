# Fine-Tuning LLMs for OpenCredit

## Overview

This guide explains how to train/fine-tune LLMs to explain credit scoring decisions made by the OpenCredit rule engine.

**Critical Understanding**: The LLM does NOT make scoring decisions. It explains decisions made by the deterministic rule engine.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         TRAINING ARCHITECTURE                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   YAML Rules → Rule Engine → Scoring Decisions                              │
│                     ↓                                                        │
│            Training Data Generator                                           │
│                     ↓                                                        │
│   Training Examples (instruction, input, output)                            │
│                     ↓                                                        │
│              Fine-tune LLM                                                   │
│                     ↓                                                        │
│   LLM learns to EXPLAIN decisions (not make them)                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Step 1: Generate Training Data

### Using the Training Pipeline

```java
@Autowired
private CreditScoringTrainingPipeline trainingPipeline;

// Generate 5000 merchant profiles → ~40,000 training examples
trainingPipeline.generateCompleteTrainingData(
    "/path/to/output",   // Output directory
    5000                  // Number of merchant profiles
);
```

### Or via REST API

```bash
# Generate training data
curl -X POST http://localhost:8080/api/v1/training/generate \
  -H "Content-Type: application/json" \
  -d '{
    "outputDir": "/data/training",
    "numProfiles": 5000
  }'
```

### Output Files

```
/data/training/
├── train_openai.jsonl      # OpenAI fine-tuning format
├── val_openai.jsonl        # Validation set
├── test_openai.jsonl       # Test set
├── train_alpaca.json       # Alpaca format (for local models)
├── train_llama.jsonl       # Llama-2 format
├── train_hf.jsonl          # HuggingFace format
└── stats.json              # Training statistics
```

---

## Step 2: Fine-Tuning Options

### Option A: OpenAI Fine-Tuning (Easiest, Best Quality)

**Pros**: Easiest, best quality, managed infrastructure
**Cons**: Costs money, data goes to OpenAI

```bash
# Install OpenAI CLI
pip install openai

# Upload training file
openai api files.create \
  -f train_openai.jsonl \
  -p fine-tune

# Create fine-tuning job
openai api fine_tunes.create \
  -t file-abc123 \
  -m gpt-3.5-turbo \
  --suffix "opencredit-v1"

# Monitor training
openai api fine_tunes.follow -i ft-xyz789

# Use fine-tuned model
curl https://api.openai.com/v1/chat/completions \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -d '{
    "model": "ft:gpt-3.5-turbo:opencredit-v1",
    "messages": [{"role": "user", "content": "Explain my credit score of 72"}]
  }'
```

**Cost Estimate**:
- Training: ~$0.008 per 1K tokens × ~2M tokens = ~$16
- Inference: ~$0.003 per 1K tokens (cheaper than base model)

### Option B: Fine-Tune FinGPT (Financial Domain Model)

**Pros**: Pre-trained on financial data, better for financial terminology
**Cons**: More complex setup

```bash
# Clone FinGPT
git clone https://github.com/AI4Finance-Foundation/FinGPT
cd FinGPT

# Install dependencies
pip install -r requirements.txt

# Prepare data in FinGPT format
python scripts/prepare_data.py \
  --input /data/training/train_hf.jsonl \
  --output /data/fingpt_training/

# Fine-tune
python fingpt/train.py \
  --model_name_or_path FinGPT/fingpt-mt_llama2-7b_lora \
  --data_path /data/fingpt_training/ \
  --output_dir ./fingpt-opencredit \
  --num_train_epochs 3 \
  --per_device_train_batch_size 4 \
  --learning_rate 2e-5 \
  --lora_r 8 \
  --lora_alpha 32
```

### Option C: Local Fine-Tuning with Llama (Full Privacy)

**Pros**: Complete data privacy, no API costs
**Cons**: Requires GPU, more complex

```bash
# Using Axolotl (recommended for local fine-tuning)
pip install axolotl

# Create config file
cat > opencredit_config.yml << EOF
base_model: meta-llama/Llama-2-7b-chat-hf
model_type: LlamaForCausalLM
tokenizer_type: LlamaTokenizer

datasets:
  - path: /data/training/train_alpaca.json
    type: alpaca

sequence_len: 4096
sample_packing: true

adapter: lora
lora_r: 8
lora_alpha: 32
lora_dropout: 0.05
lora_target_modules:
  - q_proj
  - v_proj

learning_rate: 2e-5
num_epochs: 3
micro_batch_size: 2
gradient_accumulation_steps: 4

output_dir: ./opencredit-llama
EOF

# Train
accelerate launch -m axolotl.cli.train opencredit_config.yml

# Convert to GGUF for Ollama
python llama.cpp/convert.py ./opencredit-llama --outfile opencredit.gguf
```

### Option D: Ollama with Custom Model (Simplest Local)

**Pros**: Simple deployment, runs on laptop
**Cons**: Less customization

```bash
# Create Modelfile
cat > Modelfile << EOF
FROM llama2:7b-chat

SYSTEM """
You are a credit advisor AI for OpenCredit, an open-source credit scoring platform for small merchants in India.

Your role is to EXPLAIN credit scores and decisions. All calculations are done by a deterministic rule engine.

Key principles:
1. Be clear and helpful to small business owners
2. Use simple language, avoid jargon
3. Be encouraging - help them understand how to improve
4. Use Indian currency format (₹, lakhs, crores)
5. Reference the transparent, open-source nature of scoring rules

The scoring system evaluates:
- Transaction Volume (30% weight)
- Consistency (25% weight)
- Growth Rate (15% weight)
- Bounce Rate (15% weight)
- Customer Concentration (15% weight)

Never use discriminatory factors like gender, religion, caste, or location.
"""

PARAMETER temperature 0.7
PARAMETER num_ctx 4096
EOF

# Create custom model
ollama create opencredit -f Modelfile

# Test
ollama run opencredit "Explain a credit score of 72 for a merchant with ₹1.5L monthly volume"
```

---

## Step 3: Training Data Quality

### Example Training Data

```json
{
  "messages": [
    {
      "role": "system",
      "content": "You are a credit advisor AI for OpenCredit..."
    },
    {
      "role": "user", 
      "content": "Explain this merchant's credit score in simple terms.\n\nMerchant Profile:\n- Credit Score: 72/100 (MEDIUM Risk)\n- Monthly Volume: ₹1.5L\n- Bounce Rate: 8%\n- Growth Rate: 12%\n- Concentration: 35%"
    },
    {
      "role": "assistant",
      "content": "Your credit score of 72 is good, placing you in the MEDIUM risk category. This means you're eligible for loans with standard terms.\n\n**What's working well:**\n✓ Healthy monthly transaction volume of ₹1.5 lakh\n✓ Positive growth trend of 12%\n\n**Areas for improvement:**\n• Your bounce rate of 8% is affecting your score. Aim for below 5%.\n• Customer concentration of 35% is moderate. Diversifying would help.\n\nOverall, you're in a solid position with room to improve to the LOW risk category."
    }
  ]
}
```

### Training Data Categories

| Category | % of Data | Purpose |
|----------|-----------|---------|
| score_explanation | 25% | Explain scores in simple terms |
| score_reasoning | 15% | Detailed "why this score" breakdown |
| eligibility | 15% | Loan eligibility explanations |
| recommendations | 15% | Improvement advice |
| risk_analysis | 10% | Lender-focused risk assessment |
| breakdown | 10% | Component-by-component analysis |
| faq | 5% | Common questions |
| edge_cases | 5% | Special situations |

---

## Step 4: Evaluation

### Evaluation Metrics

```python
from datasets import load_dataset
from transformers import pipeline

# Load test set
test_data = load_dataset('json', data_files='test_openai.jsonl')

# Evaluate
def evaluate_model(model_name, test_data):
    generator = pipeline('text-generation', model=model_name)
    
    metrics = {
        'accuracy': 0,      # Does response match expected?
        'helpfulness': 0,   # Is advice actionable?
        'safety': 0,        # No harmful/discriminatory content?
        'factuality': 0,    # Facts match rules?
    }
    
    for example in test_data:
        response = generator(example['instruction'] + '\n\n' + example['input'])
        
        # Compare with expected output
        # Score each metric
        # ...
    
    return metrics
```

### Manual Evaluation Checklist

- [ ] Scores explained correctly (matches rule engine output)
- [ ] Recommendations are actionable and specific
- [ ] No discriminatory language or factors mentioned
- [ ] Indian context appropriate (₹, lakhs, UPI terminology)
- [ ] Tone is encouraging but honest
- [ ] Complex concepts explained simply
- [ ] No hallucinated facts or rules

---

## Step 5: Integration

### Configure the Fine-Tuned Model

```yaml
# application.yml
llm:
  enabled: true
  provider: OPENAI  # or OLLAMA for local
  api:
    base-url: https://api.openai.com/v1
    api-key: ${LLM_API_KEY}
  model:
    # Use fine-tuned model instead of base
    openai-model: ft:gpt-3.5-turbo:your-org::opencredit-v1
    temperature: 0.7
    max-tokens: 1024
  features:
    credit-insights: true
    risk-narrative: true
    recommendations: true
    chat-interface: true
```

### For Ollama (Local)

```yaml
llm:
  enabled: true
  provider: OLLAMA
  api:
    base-url: http://localhost:11434
  model:
    ollama-model: opencredit  # Your custom model
    temperature: 0.7
```

---

## Best Practices

### 1. Keep Training Data Updated with Rules

When rules change, regenerate training data:

```java
@EventListener
public void onRulesUpdated(RulesUpdatedEvent event) {
    log.info("Rules updated to version {}, regenerating training data", 
        event.getNewVersion());
    trainingPipeline.generateCompleteTrainingData(
        trainingDataPath,
        TRAINING_SAMPLE_SIZE
    );
}
```

### 2. Monitor for Drift

```java
@Scheduled(cron = "0 0 * * * *") // Every hour
public void checkModelDrift() {
    // Compare LLM explanations with rule engine outputs
    List<TestCase> testCases = generateTestCases(100);
    
    int mismatches = 0;
    for (TestCase tc : testCases) {
        ScoringResult ruleResult = ruleEngine.calculateScore(tc.getMetrics());
        String llmExplanation = llmClient.explain(tc.getMetrics());
        
        if (!explanationMatchesResult(llmExplanation, ruleResult)) {
            mismatches++;
            log.warn("LLM drift detected for case: {}", tc);
        }
    }
    
    if (mismatches > 10) {
        alerting.send("LLM drift detected: " + mismatches + " mismatches");
    }
}
```

### 3. Hybrid Validation

Always validate LLM output against rule engine:

```java
public EnhancedCreditResponse assess(String merchantId) {
    // Rule engine makes decision (source of truth)
    ScoringResult ruleResult = ruleEngine.calculateScore(metrics);
    
    // LLM explains decision
    String explanation = llmClient.explain(metrics, ruleResult);
    
    // Validate LLM didn't hallucinate
    if (!validateExplanation(explanation, ruleResult)) {
        log.warn("LLM explanation inconsistent, using fallback");
        explanation = generateFallbackExplanation(ruleResult);
    }
    
    return EnhancedCreditResponse.builder()
        .score(ruleResult.getCreditScore())  // From rules
        .explanation(explanation)              // From LLM
        .build();
}
```

---

## Cost Comparison

| Approach | Training Cost | Per-Query Cost | Privacy | Quality |
|----------|--------------|----------------|---------|---------|
| OpenAI Fine-tune | ~$15-30 | ~$0.003/1K tok | ❌ Data to OpenAI | ⭐⭐⭐⭐⭐ |
| FinGPT Fine-tune | ~$50 (GPU) | Free (local) | ✅ Full privacy | ⭐⭐⭐⭐ |
| Llama Local | ~$50 (GPU) | Free (local) | ✅ Full privacy | ⭐⭐⭐ |
| Ollama Custom | Free | Free | ✅ Full privacy | ⭐⭐⭐ |
| No Fine-tune | Free | ~$0.01/1K tok | ❌ Data to API | ⭐⭐ |

---

## Recommended Approach

### For Production (OpenCredit Platform)

```
1. Start with: OpenAI GPT-4 (no fine-tuning) for MVP
2. Then: Fine-tune GPT-3.5-turbo for cost reduction
3. Eventually: Train FinGPT/Llama for full privacy
```

### For Enterprise/Bank Deployment

```
1. Use: Local Llama fine-tuned model
2. Deploy: On-premise or private cloud
3. Reason: Full data privacy, regulatory compliance
```

### For Community/Open Source

```
1. Train: Open model (Llama/Mistral)
2. Share: Model weights on HuggingFace
3. Benefit: Anyone can use without API costs
```

---

## Summary

| What | Use Rule Engine | Use LLM |
|------|----------------|---------|
| Calculate score | ✅ YES | ❌ NO |
| Make decision | ✅ YES | ❌ NO |
| Explain score | ❌ Basic | ✅ YES |
| Recommendations | ❌ Generic | ✅ YES |
| Chat interface | ❌ None | ✅ YES |
| Audit/compliance | ✅ YES | ❌ NO |

**Final Answer**: 
- **RULES for decisions** (deterministic, auditable, compliant)
- **LLM for explanations** (natural, personalized, engaging)
- **Train LLM FROM rules** (not to replace them)
