# Quick Start: Mistral Integration

This guide helps you quickly get started with Mistral Devstral Small for credit score explanations.

## Prerequisites

- Java 17+ installed
- Maven 3.8+ installed
- Mistral API key from [console.mistral.ai](https://console.mistral.ai)

## Setup (5 minutes)

### Step 1: Get Your Mistral API Key

1. Sign up at [console.mistral.ai](https://console.mistral.ai)
2. Create a new API key
3. Copy the key (starts with `...`)

### Step 2: Configure Environment

**Option A: Environment Variables (Recommended)**

```bash
export LLM_PROVIDER=MISTRAL
export LLM_BASE_URL=https://api.mistral.ai/v1
export LLM_API_KEY=your_mistral_api_key_here
export MISTRAL_MODEL=devstral-small-2505
export LLM_ENABLED=true
```

**Option B: Edit application.yml**

```yaml
llm:
  enabled: true
  provider: MISTRAL
  api:
    base-url: https://api.mistral.ai/v1
    api-key: your_mistral_api_key_here
  model:
    mistral-model: devstral-small-2505
```

### Step 3: Start the Application

```bash
# Build
mvn clean install

# Run
mvn spring-boot:run
```

Or with environment variables:

```bash
LLM_PROVIDER=MISTRAL LLM_API_KEY=your_key mvn spring-boot:run
```

### Step 4: Verify Integration

Check logs for:
```
INFO  c.l.v.c.config.LlmConfig - LLM Provider: MISTRAL
INFO  c.l.v.c.config.LlmConfig - Mistral Model: devstral-small-2505
```

## Test the Integration

### 1. Basic Health Check

```bash
curl http://localhost:8080/actuator/health
```

### 2. Test with Demo Scenario

```bash
curl -X POST "http://localhost:8080/api/v1/demo/assess/healthy/TEST_MERCHANT_001"
```

**Expected Response:**
```json
{
  "merchantId": "TEST_MERCHANT_001",
  "creditScore": 85,
  "riskCategory": "LOW",
  "eligible": true,
  "eligibleLoanAmount": 450000.00,
  "assessmentDate": "2025-12-09T..."
}
```

### 3. Get AI-Powered Credit Explanation

```bash
curl "http://localhost:8080/api/v1/insights/TEST_MERCHANT_001/narrative"
```

**Expected Response (Mistral-generated):**
```json
{
  "merchantId": "TEST_MERCHANT_001",
  "narrative": "Your credit score of 85 places you in the LOW risk category, which is excellent! This score reflects strong financial health across all key metrics.\n\nHere's what contributed to your score:\n\n• Volume Score (30%): 27/30 - Your average monthly UPI volume of ₹150,000 demonstrates healthy business activity...",
  "generatedAt": "2025-12-09T..."
}
```

### 4. Get Personalized Recommendations

```bash
curl "http://localhost:8080/api/v1/insights/TEST_MERCHANT_001/recommendations"
```

### 5. Get 30-60-90 Day Improvement Plan

```bash
curl "http://localhost:8080/api/v1/insights/TEST_MERCHANT_001/improvement-plan"
```

### 6. Interactive Chat

**Start a session:**
```bash
curl -X POST "http://localhost:8080/api/v1/chat/session/start" \
  -H "Content-Type: application/json" \
  -d '{
    "merchantId": "TEST_MERCHANT_001"
  }'
```

**Send a question:**
```bash
curl -X POST "http://localhost:8080/api/v1/chat/message" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "session_xyz",
    "merchantId": "TEST_MERCHANT_001",
    "message": "Why is my credit score 85 and what can I do to improve it?"
  }'
```

## Demo Scenarios

Test different credit profiles:

| Scenario | Command | Expected Score | Risk Category |
|----------|---------|----------------|---------------|
| Healthy Business | `curl -X POST "http://localhost:8080/api/v1/demo/assess/healthy/MERCHANT_A"` | 80-90 | LOW |
| Growing Business | `curl -X POST "http://localhost:8080/api/v1/demo/assess/growing/MERCHANT_B"` | 70-80 | MEDIUM |
| High Volume | `curl -X POST "http://localhost:8080/api/v1/demo/assess/high-volume/MERCHANT_C"` | 75-85 | LOW/MEDIUM |
| Inconsistent | `curl -X POST "http://localhost:8080/api/v1/demo/assess/inconsistent/MERCHANT_D"` | 50-65 | HIGH/MEDIUM |
| New Business | `curl -X POST "http://localhost:8080/api/v1/demo/assess/new-business/MERCHANT_E"` | 40-60 | HIGH |

Then get Mistral's explanation for each:
```bash
curl "http://localhost:8080/api/v1/insights/MERCHANT_A/narrative"
```

## API Endpoints Reference

### Assessment Endpoints

- `POST /credit-assessment/analyze/{merchantId}` - Basic credit assessment
- `POST /credit-assessment/analyze-enhanced/{merchantId}` - Assessment + AI insights
- `GET /credit-assessment/report/{merchantId}` - Get detailed report
- `GET /credit-assessment/report/{merchantId}/pdf` - Download PDF report

### AI Insights Endpoints (Powered by Mistral)

- `GET /insights/{merchantId}` - All AI insights (narrative, risk, recommendations, plan)
- `GET /insights/{merchantId}/narrative` - Natural language credit explanation
- `GET /insights/{merchantId}/risk-analysis` - Detailed risk assessment
- `GET /insights/{merchantId}/recommendations` - Actionable improvement tips
- `GET /insights/{merchantId}/improvement-plan` - 30-60-90 day roadmap

### Chat Endpoints (Powered by Mistral)

- `POST /chat/session/start` - Start new chat session
- `POST /chat/message` - Send message to Mistral
- `GET /chat/history/{sessionId}` - Get conversation history

### Rules & Transparency

- `GET /rules/version` - Get rules version
- `GET /rules/full` - View all scoring and eligibility rules
- `GET /rules/methodology` - Understand scoring methodology
- `POST /rules/simulate` - What-if scenario simulation

## What Mistral Explains

Mistral Devstral Small generates explanations for:

### 1. Credit Score Breakdown
- Overall score (0-100)
- Individual component scores:
  - Volume (30%): Monthly UPI transaction volumes
  - Consistency (25%): Income stability
  - Growth (15%): Business trajectory
  - Bounce Rate (15%): Payment reliability
  - Concentration (15%): Customer diversification

### 2. Risk Category
- LOW (80-100): Best loan terms
- MEDIUM (60-79): Standard terms
- HIGH (0-59): Limited terms

### 3. Eligibility Status
- Why approved or denied
- Which rules passed/failed
- Specific recommendations to become eligible

### 4. Loan Parameters
- Eligible amount calculation
- Interest rate reasoning
- Tenure determination

### 5. Improvement Strategies
- Specific actions for each component
- Expected impact on score
- Timeline for improvements

## Example: Complete Flow

```bash
# 1. Analyze a merchant's credit
curl -X POST "http://localhost:8080/api/v1/credit-assessment/analyze-enhanced/MERCHANT_123"

# Response:
{
  "merchantId": "MERCHANT_123",
  "creditScore": 72,
  "riskCategory": "MEDIUM",
  "eligible": true,
  "eligibleLoanAmount": 180000.00,
  "componentScores": {
    "volume": 24.0,
    "consistency": 18.8,
    "growth": 10.5,
    "bounceRate": 12.0,
    "concentration": 11.2
  },
  "aiInsights": {
    "narrative": "Your credit score of 72 places you in the MEDIUM risk category...",
    "riskAnalysis": "Your business demonstrates solid fundamentals...",
    "recommendations": [
      "Reduce customer concentration from 45% to below 40%",
      "Maintain consistent monthly volumes above ₹150,000"
    ]
  }
}

# 2. Get detailed narrative explanation
curl "http://localhost:8080/api/v1/insights/MERCHANT_123/narrative"

# 3. Get improvement plan
curl "http://localhost:8080/api/v1/insights/MERCHANT_123/improvement-plan"

# 4. Ask specific questions via chat
curl -X POST "http://localhost:8080/api/v1/chat/session/start" \
  -d '{"merchantId": "MERCHANT_123"}'

curl -X POST "http://localhost:8080/api/v1/chat/message" \
  -d '{
    "sessionId": "session_abc",
    "merchantId": "MERCHANT_123",
    "message": "How can I increase my credit score to 80?"
  }'

# 5. Download PDF report with AI insights
curl "http://localhost:8080/api/v1/credit-assessment/report/MERCHANT_123/pdf" \
  --output report.pdf
```

## Configuration Options

### Model Settings

```yaml
llm:
  model:
    mistral-model: devstral-small-2505  # Model to use
    temperature: 0.7                     # Creativity (0.0-1.0)
    max-tokens: 2048                     # Response length
    top-p: 0.9                          # Diversity
```

**Recommended Settings:**
- **Credit Explanations**: temperature=0.7, max-tokens=2048
- **Short Answers**: temperature=0.5, max-tokens=512
- **Detailed Plans**: temperature=0.8, max-tokens=3000

### Switching Providers

Change provider without code changes:

```bash
# Use Mistral (cost-effective)
export LLM_PROVIDER=MISTRAL
export LLM_API_KEY=your_mistral_key

# Use OpenAI (premium quality)
export LLM_PROVIDER=OPENAI
export LLM_API_KEY=your_openai_key

# Use Ollama (local, privacy-focused)
export LLM_PROVIDER=OLLAMA
export LLM_BASE_URL=http://localhost:11434
```

## Troubleshooting

### Issue: "LLM API key not configured"

**Solution:**
```bash
export LLM_API_KEY=your_mistral_api_key
```

### Issue: "Rate limit exceeded"

**Solution:** Check your Mistral plan at console.mistral.ai. Free tier has limits.

### Issue: "Model not found: devstral-small-2505"

**Solution:** Ensure you're using the correct model name and have API access.

### Issue: Empty or truncated responses

**Solution:** Increase max-tokens:
```yaml
llm:
  model:
    max-tokens: 4096
```

### Issue: Slow responses

**Solution:**
1. Reduce max-tokens to 1024 for faster responses
2. Use lower temperature (0.5) for more focused answers
3. Check network latency to Mistral API

## Monitoring

View logs for LLM activity:

```bash
tail -f logs/application.log | grep -i "mistral\|llm"
```

Look for:
- API call success/failure
- Response times
- Token usage
- Error messages

## Next Steps

1. ✅ Test all demo scenarios
2. ✅ Integrate with your frontend
3. ✅ Customize prompts for your use case
4. ✅ Monitor usage and costs
5. ✅ Set up production deployment

## Support

- **Mistral Docs**: https://docs.mistral.ai
- **OpenCredit GitHub**: https://github.com/lumexpay/open-credit-core
- **API Reference**: http://localhost:8080/swagger-ui.html

## Cost Estimates

Mistral Devstral Small pricing (check current rates at console.mistral.ai):

| Operation | Tokens | Estimated Cost |
|-----------|--------|----------------|
| Simple narrative | ~500 | ~$0.01 |
| Detailed analysis | ~1500 | ~$0.03 |
| Chat message | ~300 | ~$0.006 |
| Improvement plan | ~2000 | ~$0.04 |

**Monthly estimate for 1000 assessments with full insights:** ~$30-50

## Production Checklist

- [ ] Secure API key storage (use secrets manager)
- [ ] Set up rate limiting
- [ ] Configure monitoring and alerting
- [ ] Set appropriate timeout values
- [ ] Implement response caching
- [ ] Add fallback for LLM failures
- [ ] Test with production data volume
- [ ] Review and optimize prompts
- [ ] Set up cost tracking
- [ ] Configure log retention

---

**Ready to integrate Mistral!** For more details, see [MISTRAL_INTEGRATION_GUIDE.md](./MISTRAL_INTEGRATION_GUIDE.md)
