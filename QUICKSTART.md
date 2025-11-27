# Credit Assessment Engine - Quick Start Guide

## 🚀 Get Started in 5 Minutes

### Step 1: Start PostgreSQL (Using Docker)

```bash
cd opencredit-core
docker-compose up -d postgres
```

**Verify database is running:**
```bash
docker ps | grep credit-assessment-db
```

### Step 2: Configure the Application

The default configuration in `application.properties` should work with the Docker setup.

**Optional:** If using your own PostgreSQL instance, update:
```properties
spring.datasource.url=jdbc:postgresql://your-host:5432/credit_assessment_db
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### Step 3: Build and Run

```bash
# Build
mvn clean install

# Run
mvn spring-boot:run
```

**Expected output:**
```
Started CreditAssessmentEngineApplication in X.XXX seconds
```

### Step 4: Verify Installation

**Check health:**
```bash
curl http://localhost:8080/actuator/health
```

**Expected response:**
```json
{"status":"UP"}
```

### Step 5: Test the API

#### Create a Mock Assessment

Since you need UPI transaction data, let's first understand the data model.

**Option A: Mock the UPI Platform Client (for testing)**

Create a test profile with mocked data:

```bash
# Run with test profile
mvn spring-boot:run -Dspring-boot.run.profiles=test
```

**Option B: Call with Real Data**

If you have a running UPI platform:

```bash
curl -X POST http://localhost:8080/api/v1/credit-assessment/analyze/MERCHANT_001
```

### Step 6: View API Documentation

Open your browser to:
```
http://localhost:8080/swagger-ui.html
```

Here you can:
- ✅ View all available endpoints
- ✅ Test APIs interactively
- ✅ See request/response schemas
- ✅ Download OpenAPI specification

## 📝 Common Use Cases

### Use Case 1: Assess a New Merchant

```bash
curl -X POST "http://localhost:8080/api/v1/credit-assessment/analyze/MERCHANT_123" \
  -H "Content-Type: application/json"
```

### Use Case 2: Get Loan Eligibility

```bash
curl -X GET "http://localhost:8080/api/v1/credit-assessment/eligibility/MERCHANT_123"
```

### Use Case 3: Download Credit Report

```bash
curl -X GET "http://localhost:8080/api/v1/credit-assessment/report/MERCHANT_123/pdf" \
  --output credit_report.pdf
```

### Use Case 4: Find All Eligible Merchants

```bash
curl -X GET "http://localhost:8080/api/v1/credit-assessment/eligible"
```

### Use Case 5: Get Merchants by Risk

```bash
# Get all LOW risk merchants
curl -X GET "http://localhost:8080/api/v1/credit-assessment/risk/LOW"
```

### Use Case 6: Search by Credit Score Range

```bash
# Get merchants with score 70-90
curl -X GET "http://localhost:8080/api/v1/credit-assessment/score-range?minScore=70&maxScore=90"
```

## 🧪 Testing with Sample Data

### Option 1: Use H2 Console (For Quick Testing)

1. Modify `application.properties`:
```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver
spring.h2.console.enabled=true
```

2. Access H2 Console: `http://localhost:8080/h2-console`

### Option 2: Load Sample Data via SQL

```sql
-- Insert a sample merchant assessment
INSERT INTO credit_assessments (
    assessment_id, merchant_id, assessment_date, 
    credit_score, risk_category, is_eligible,
    last_3_months_volume, average_monthly_volume, transaction_count,
    consistency_score, growth_rate, bounce_rate, customer_concentration,
    eligible_loan_amount, max_tenure, recommended_interest_rate,
    created_at, updated_at
) VALUES (
    gen_random_uuid(), 
    'MERCHANT_SAMPLE', 
    NOW(),
    85, 
    'LOW', 
    true,
    450000.00, 
    150000.00, 
    120,
    82.50, 
    15.30, 
    3.20, 
    25.40,
    45000.00, 
    365, 
    18.00,
    NOW(),
    NOW()
);
```

### Option 3: Use JUnit Tests

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=CreditScoringServiceTest#testAssessMerchant_HighVolume_LowRisk
```

## 🔧 Configuration Tips

### Enable Detailed Logging

```properties
logging.level.com.sentient.fintech.credit=DEBUG
logging.level.org.hibernate.SQL=DEBUG
```

### Change Server Port

```properties
server.port=9090
```

### Configure UPI Platform

```properties
upi.platform.base-url=http://your-upi-platform.com
upi.platform.api-key=your-secret-key
```

### Configure PDF Reports Location

```properties
pdf.reports.directory=/var/credit-reports
pdf.reports.base-url=https://your-domain.com/reports
```

## 📊 Monitoring

### Check Application Metrics

```bash
curl http://localhost:8080/actuator/metrics
```

### View Database Statistics

```bash
# Using pgAdmin
http://localhost:5050

# Login: admin@credit.com / admin
```

## 🐛 Troubleshooting

### Problem: Database connection refused

**Solution:**
```bash
# Check if PostgreSQL is running
docker ps

# Check logs
docker logs credit-assessment-db

# Restart database
docker-compose restart postgres
```

### Problem: Port 8080 already in use

**Solution:**
```bash
# Find process using port 8080
lsof -i :8080

# Kill the process or change application port
# In application.properties:
server.port=9090
```

### Problem: PDF generation fails

**Solution:**
```bash
# Ensure reports directory exists
mkdir -p ./reports

# Check permissions
chmod 755 ./reports
```

### Problem: UPI Platform connection timeout

**Solution:**
- Verify UPI platform URL is correct
- Check API key validity
- Ensure network connectivity
- Review firewall rules

## 📚 Next Steps

1. **Integrate with UPI Platform:** Update `UpiPlatformClient` with your actual UPI platform endpoints

2. **Customize Scoring:** Adjust weights in `CreditScoringService` based on your risk appetite

3. **Add Authentication:** Implement Spring Security for API protection

4. **Set Up Monitoring:** Integrate with Prometheus/Grafana for production monitoring

5. **Configure Alerts:** Set up email/SMS alerts for critical events

6. **Scale Horizontally:** Deploy multiple instances behind a load balancer

## 📞 Need Help?

- **Documentation:** See `README.md` for detailed documentation
- **API Docs:** `http://localhost:8080/swagger-ui.html`
- **Issues:** Create an issue in the repository
- **Email:** support@sentient.com

---

**Ready to assess your first merchant? Let's go! 🚀**