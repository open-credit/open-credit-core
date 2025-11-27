# Credit Assessment Engine - API Examples

## 🔧 Prerequisites

```bash
# Set base URL
export BASE_URL="http://localhost:8080/api/v1/credit-assessment"

# Optional: Set merchant ID for testing
export MERCHANT_ID="MERCHANT_001"
```

## 1️⃣ Assessment Operations

### 1.1 Analyze Merchant Credit

**Request:**
```bash
curl -X POST "${BASE_URL}/analyze/${MERCHANT_ID}" \
  -H "Content-Type: application/json" \
  | jq '.'
```

**Success Response (201 Created):**
```json
{
  "assessmentId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "merchantId": "MERCHANT_001",
  "assessmentDate": "2024-11-24T14:30:00",
  "financialMetrics": {
    "last3MonthsVolume": 450000.00,
    "last6MonthsVolume": 850000.00,
    "last12MonthsVolume": 1600000.00,
    "averageMonthlyVolume": 150000.00,
    "averageTransactionValue": 2500.00,
    "transactionCount": 640
  },
  "performanceMetrics": {
    "consistencyScore": 82.50,
    "growthRate": 15.30,
    "bounceRate": 3.20,
    "customerConcentration": 25.40
  },
  "creditScore": 85,
  "riskCategory": "LOW",
  "eligibility": {
    "eligibleLoanAmount": 45000.00,
    "maxTenure": 365,
    "recommendedInterestRate": 18.00
  },
  "isEligible": true,
  "ineligibilityReason": null,
  "warnings": [],
  "strengths": [
    "Highly consistent business: 82.50/100",
    "Rapid growth: 15.30% increase",
    "Excellent payment success rate: 97%",
    "High transaction volume: ₹1,50,000.00/month"
  ],
  "reportUrl": "http://localhost:8080/reports/credit_report_MERCHANT_001_20241124_143000.pdf"
}
```

### 1.2 Get Latest Credit Report

**Request:**
```bash
curl -X GET "${BASE_URL}/report/${MERCHANT_ID}" | jq '.'
```

### 1.3 Get Assessment History

**Request:**
```bash
curl -X GET "${BASE_URL}/history/${MERCHANT_ID}" | jq '.'
```

**Response:** Array of assessments, ordered by date (newest first)

### 1.4 Get Eligibility Details

**Request:**
```bash
curl -X GET "${BASE_URL}/eligibility/${MERCHANT_ID}" | jq '.'
```

**Success Response (200 OK):**
```json
{
  "merchantId": "MERCHANT_001",
  "isEligible": true,
  "ineligibilityReason": null,
  "eligibleLoanAmount": 45000.00,
  "maxTenure": 365,
  "recommendedInterestRate": 18.00,
  "creditScore": 85,
  "riskCategory": "LOW"
}
```

**Ineligible Merchant Example:**
```json
{
  "merchantId": "MERCHANT_LOW_VOLUME",
  "isEligible": false,
  "ineligibilityReason": "Average monthly volume too low (minimum ₹25,000)",
  "eligibleLoanAmount": null,
  "maxTenure": null,
  "recommendedInterestRate": null,
  "creditScore": 42,
  "riskCategory": "HIGH"
}
```

### 1.5 Download PDF Report

**Request:**
```bash
curl -X GET "${BASE_URL}/report/${MERCHANT_ID}/pdf" \
  --output "credit_report_${MERCHANT_ID}.pdf"
```

**Alternative (open in browser):**
```bash
open "${BASE_URL}/report/${MERCHANT_ID}/pdf"
# or
xdg-open "${BASE_URL}/report/${MERCHANT_ID}/pdf"
```

### 1.6 Re-assess Merchant

**Request:**
```bash
curl -X POST "${BASE_URL}/re-assess/${MERCHANT_ID}" \
  -H "Content-Type: application/json" \
  | jq '.'
```

**Use Cases:**
- Manual trigger after merchant improves metrics
- After updating merchant business information
- For periodic compliance checks

## 2️⃣ Query Operations

### 2.1 Get All Eligible Merchants

**Request:**
```bash
curl -X GET "${BASE_URL}/eligible" | jq '.'
```

**Response:** Array of eligible merchant assessments

**Use Cases:**
- Lending marketplace dashboard
- Loan origination pipeline
- Business development leads

### 2.2 Get Merchants by Risk Category

**Low Risk Merchants:**
```bash
curl -X GET "${BASE_URL}/risk/LOW" | jq '.'
```

**Medium Risk Merchants:**
```bash
curl -X GET "${BASE_URL}/risk/MEDIUM" | jq '.'
```

**High Risk Merchants:**
```bash
curl -X GET "${BASE_URL}/risk/HIGH" | jq '.'
```

**Use Cases:**
- Risk-based loan allocation
- Portfolio management
- Interest rate determination

### 2.3 Get Merchants by Credit Score Range

**Premium Merchants (80-100):**
```bash
curl -X GET "${BASE_URL}/score-range?minScore=80&maxScore=100" | jq '.'
```

**Good Credit (70-79):**
```bash
curl -X GET "${BASE_URL}/score-range?minScore=70&maxScore=79" | jq '.'
```

**Fair Credit (60-69):**
```bash
curl -X GET "${BASE_URL}/score-range?minScore=60&maxScore=69" | jq '.'
```

**Use Cases:**
- Tiered pricing strategies
- Targeted marketing campaigns
- Credit limit adjustments

### 2.4 Get Specific Assessment by ID

**Request:**
```bash
export ASSESSMENT_ID="f47ac10b-58cc-4372-a567-0e02b2c3d479"

curl -X GET "${BASE_URL}/${ASSESSMENT_ID}" | jq '.'
```

## 3️⃣ Advanced Queries

### 3.1 Get Merchants with High Growth

```bash
# Get all assessments, filter for growth > 20%
curl -X GET "${BASE_URL}/eligible" | \
  jq '.[] | select(.performanceMetrics.growthRate > 20)'
```

### 3.2 Find Merchants with Low Bounce Rate

```bash
# Eligible merchants with bounce rate < 5%
curl -X GET "${BASE_URL}/eligible" | \
  jq '.[] | select(.performanceMetrics.bounceRate < 5)'
```

### 3.3 Get High-Value Merchants

```bash
# Merchants with avg monthly volume > 5 lakh
curl -X GET "${BASE_URL}/eligible" | \
  jq '.[] | select(.financialMetrics.averageMonthlyVolume > 500000)'
```

## 4️⃣ Batch Operations

### 4.1 Assess Multiple Merchants

```bash
#!/bin/bash
MERCHANTS=("MERCHANT_001" "MERCHANT_002" "MERCHANT_003")

for merchant in "${MERCHANTS[@]}"; do
  echo "Assessing $merchant..."
  curl -X POST "${BASE_URL}/analyze/$merchant" -H "Content-Type: application/json"
  echo ""
  sleep 1  # Rate limiting
done
```

### 4.2 Export All Eligible Merchants to CSV

```bash
curl -X GET "${BASE_URL}/eligible" | \
  jq -r '.[] | [.merchantId, .creditScore, .riskCategory, .eligibility.eligibleLoanAmount] | @csv' \
  > eligible_merchants.csv
```

### 4.3 Generate Reports for All Eligible Merchants

```bash
#!/bin/bash
MERCHANTS=$(curl -s "${BASE_URL}/eligible" | jq -r '.[].merchantId')

for merchant in $MERCHANTS; do
  echo "Downloading report for $merchant..."
  curl -X GET "${BASE_URL}/report/$merchant/pdf" \
    --output "reports/credit_report_$merchant.pdf"
  sleep 1
done
```

## 5️⃣ Health and Monitoring

### 5.1 Application Health Check

```bash
curl -X GET "http://localhost:8080/actuator/health" | jq '.'
```

**Expected Response:**
```json
{
  "status": "UP"
}
```

### 5.2 Application Info

```bash
curl -X GET "http://localhost:8080/actuator/info" | jq '.'
```

### 5.3 Metrics

```bash
curl -X GET "http://localhost:8080/actuator/metrics" | jq '.'
```

## 6️⃣ Error Handling Examples

### 6.1 Merchant Not Found

**Request:**
```bash
curl -X GET "${BASE_URL}/report/NONEXISTENT_MERCHANT" -w "\nHTTP Status: %{http_code}\n"
```

**Response (404 Not Found):**
```
HTTP Status: 404
```

### 6.2 Invalid Risk Category

**Request:**
```bash
curl -X GET "${BASE_URL}/risk/INVALID" -w "\nHTTP Status: %{http_code}\n"
```

**Response (400 Bad Request):**
```
HTTP Status: 400
```

### 6.3 Invalid Score Range

**Request:**
```bash
curl -X GET "${BASE_URL}/score-range?minScore=150&maxScore=200" -w "\nHTTP Status: %{http_code}\n"
```

**Response (400 Bad Request):**
```
HTTP Status: 400
```

## 7️⃣ Integration Examples

### 7.1 Python Integration

```python
import requests
import json

BASE_URL = "http://localhost:8080/api/v1/credit-assessment"

def assess_merchant(merchant_id):
    url = f"{BASE_URL}/analyze/{merchant_id}"
    response = requests.post(url)
    
    if response.status_code == 201:
        return response.json()
    else:
        raise Exception(f"Assessment failed: {response.status_code}")

# Usage
assessment = assess_merchant("MERCHANT_001")
print(f"Credit Score: {assessment['creditScore']}")
print(f"Eligible: {assessment['isEligible']}")
```

### 7.2 JavaScript/Node.js Integration

```javascript
const axios = require('axios');

const BASE_URL = 'http://localhost:8080/api/v1/credit-assessment';

async function assessMerchant(merchantId) {
    try {
        const response = await axios.post(`${BASE_URL}/analyze/${merchantId}`);
        return response.data;
    } catch (error) {
        console.error('Assessment failed:', error.message);
        throw error;
    }
}

// Usage
assessMerchant('MERCHANT_001')
    .then(assessment => {
        console.log(`Credit Score: ${assessment.creditScore}`);
        console.log(`Eligible: ${assessment.isEligible}`);
    });
```

### 7.3 Java Integration

```java
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

public class CreditAssessmentClient {
    
    private static final String BASE_URL = "http://localhost:8080/api/v1/credit-assessment";
    private final RestTemplate restTemplate = new RestTemplate();
    
    public CreditAssessmentResponse assessMerchant(String merchantId) {
        String url = BASE_URL + "/analyze/" + merchantId;
        
        ResponseEntity<CreditAssessmentResponse> response = 
            restTemplate.postForEntity(url, null, CreditAssessmentResponse.class);
        
        return response.getBody();
    }
}
```

## 8️⃣ Performance Testing

### 8.1 Load Test with Apache Bench

```bash
# Test assessment endpoint
ab -n 100 -c 10 -p /dev/null \
   -T "application/json" \
   -m POST \
   "http://localhost:8080/api/v1/credit-assessment/analyze/MERCHANT_001"
```

### 8.2 Load Test with wrk

```bash
wrk -t10 -c100 -d30s \
    -s post.lua \
    http://localhost:8080/api/v1/credit-assessment/analyze/MERCHANT_001
```

## 🔐 Authentication Examples (If Implemented)

### With Bearer Token

```bash
export TOKEN="your-jwt-token-here"

curl -X POST "${BASE_URL}/analyze/${MERCHANT_ID}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  | jq '.'
```

### With API Key

```bash
export API_KEY="your-api-key-here"

curl -X POST "${BASE_URL}/analyze/${MERCHANT_ID}" \
  -H "X-API-Key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  | jq '.'
```

## 📝 Tips and Best Practices

1. **Rate Limiting:** Add delays between requests in batch operations
2. **Error Handling:** Always check HTTP status codes
3. **Retries:** Implement exponential backoff for failed requests
4. **Caching:** Cache frequently accessed assessments
5. **Pagination:** Consider pagination for large result sets
6. **Monitoring:** Log all API calls for audit trails

---

**Need more examples? Check the Swagger UI at `http://localhost:8080/swagger-ui.html`**