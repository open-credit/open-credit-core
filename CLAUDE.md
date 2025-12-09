# CLAUDE.md - AI Assistant Guide for OpenCredit

This document provides comprehensive guidance for AI assistants (like Claude, ChatGPT, etc.) working with the OpenCredit codebase. It explains the architecture, conventions, workflows, and critical principles that must be followed when making changes.

---

## Table of Contents

- [Project Overview](#project-overview)
- [Critical Principles](#critical-principles)
- [Codebase Structure](#codebase-structure)
- [Technology Stack](#technology-stack)
- [Architecture & Design](#architecture--design)
- [Development Workflows](#development-workflows)
- [Coding Conventions](#coding-conventions)
- [Testing Strategy](#testing-strategy)
- [Common Tasks](#common-tasks)
- [Troubleshooting](#troubleshooting)

---

## Project Overview

### What is OpenCredit?

OpenCredit is an **open-source credit assessment engine** that analyzes UPI transaction history to calculate credit scores, determine loan eligibility, and assess risk for small merchants and businesses in India.

**Core Mission**: Financial inclusion through transparent, auditable, and fair credit scoring.

### Key Differentiators

1. **Transparency First**: All scoring rules are publicly visible in YAML files
2. **Rule-Based Decisions**: Deterministic, auditable decisions (not ML black-box)
3. **Hybrid Architecture**: Rules make decisions, LLM explains them
4. **RBI Compliant**: Designed for regulatory requirements
5. **Community-Governed**: Open for contributions and improvements

### Target Users

- Small merchants (kirana stores, street vendors)
- Farmers & MSMEs
- Cooperative societies
- NBFCs & Small Finance Banks
- First-time borrowers without traditional credit history

---

## Critical Principles

### 🔴 NEVER VIOLATE THESE RULES

#### 1. Rules Engine is the Source of Truth

**THE MOST IMPORTANT PRINCIPLE**: The **Rule Engine MUST make ALL credit decisions**. The LLM is ONLY for explanations.

| Function | Rule Engine | LLM |
|----------|-------------|-----|
| Calculate credit score | ✅ ALWAYS | ❌ NEVER |
| Make loan decision | ✅ ALWAYS | ❌ NEVER |
| Determine eligibility | ✅ ALWAYS | ❌ NEVER |
| Set loan amount | ✅ ALWAYS | ❌ NEVER |
| Explain decisions | ⚠️ Basic | ✅ Enhanced |
| Chat with users | ❌ NO | ✅ YES |

**Why?**
- **Regulatory Compliance**: RBI requires deterministic, auditable decisions
- **Legal Defensibility**: "AI decided" is not defensible in court
- **Consistency**: Same inputs must produce same outputs every time
- **Auditability**: Every decision must be traceable to specific rules

**When adding ANY new feature**, ask: "Does this involve a credit decision?" If yes, it MUST go in the rules engine, not LLM.

Read the full guide: `docs/RULE_VS_LLM_DECISION_GUIDE.md`

#### 2. All Scoring Logic Must Be in YAML

**NEVER** hardcode scoring logic in Java. All rules must be in YAML files:
- `src/main/resources/rules/scoring-rules.yaml`
- `src/main/resources/rules/eligibility-rules.yaml`

**Example of WRONG approach:**
```java
// ❌ WRONG - Hardcoded logic
public int calculateVolumeScore(double volume) {
    if (volume >= 500000) return 100;
    if (volume >= 200000) return 80;
    return 60;
}
```

**Example of CORRECT approach:**
```java
// ✅ CORRECT - Load from YAML rules
ScoringRules rules = ruleEngine.loadRules();
int score = ruleEngine.evaluateVolumeScore(volume, rules.getVolumeComponent());
```

#### 3. No Discriminatory Factors

The system must NEVER use or store:
- Gender
- Religion
- Caste
- Race
- Location-based discrimination
- Personal social media
- Family background
- Education level

These are explicitly excluded in `scoring-rules.yaml` metadata.

#### 4. Maintain Backwards Compatibility

When modifying scoring rules:
- ✅ Add new rules with version increments
- ✅ Provide migration paths for existing data
- ❌ Do NOT break existing assessments
- ❌ Do NOT retroactively harm merchant scores

#### 5. Security & Privacy

- NEVER log sensitive merchant data
- NEVER expose PII in error messages
- Use parameterized queries (prevent SQL injection)
- Validate all inputs rigorously
- Secure API endpoints appropriately

---

## Codebase Structure

### Directory Layout

```
open-credit-core/
├── src/
│   ├── main/
│   │   ├── java/com/lumexpay/vortexa/credit/
│   │   │   ├── config/               # Configuration classes
│   │   │   │   ├── CreditAssessmentConfig.java
│   │   │   │   ├── LlmConfig.java
│   │   │   │   └── UpiPlatformConfig.java
│   │   │   ├── controller/           # REST API endpoints
│   │   │   │   ├── CreditAssessmentController.java
│   │   │   │   ├── InsightsController.java
│   │   │   │   ├── RulesController.java
│   │   │   │   ├── ChatController.java
│   │   │   │   ├── DemoController.java
│   │   │   │   └── TrainingController.java
│   │   │   ├── service/              # Business logic
│   │   │   │   ├── CreditAssessmentService.java  # Main orchestrator
│   │   │   │   ├── CreditScoringService.java     # Score calculation
│   │   │   │   ├── MetricsCalculationService.java
│   │   │   │   ├── CreditInsightsService.java    # LLM integration
│   │   │   │   ├── CreditChatService.java
│   │   │   │   └── PdfReportService.java
│   │   │   ├── repository/           # Data access
│   │   │   │   └── CreditAssessmentRepository.java
│   │   │   ├── rules/                # Rules engine
│   │   │   │   ├── RuleEngine.java
│   │   │   │   └── model/ScoringRules.java
│   │   │   ├── client/               # External integrations
│   │   │   │   ├── UpiPlatformClient.java
│   │   │   │   ├── LlmClient.java
│   │   │   │   └── MockTransactionDataProvider.java
│   │   │   ├── dto/                  # Data transfer objects
│   │   │   ├── model/                # JPA entities
│   │   │   ├── exception/            # Exception handling
│   │   │   ├── util/                 # Utilities
│   │   │   ├── scheduler/            # Background jobs
│   │   │   └── llm/                  # LLM training utilities
│   │   └── resources/
│   │       ├── application.yml        # Main configuration
│   │       ├── rules/                 # **CRITICAL** - Scoring rules
│   │       │   ├── scoring-rules.yaml
│   │       │   ├── eligibility-rules.yaml
│   │       │   ├── README.md
│   │       │   └── CONTRIBUTING.md
│   │       └── schema.sql
│   └── test/
│       └── java/com/lumexpay/vortexa/credit/  # Unit & integration tests
├── docs/                              # Documentation
│   ├── RULE_VS_LLM_DECISION_GUIDE.md
│   ├── LLM_VS_RULES_COMPARISON.md
│   └── FINE_TUNING_GUIDE.md
├── README.md                          # Main documentation
├── QUICKSTART.md                      # Quick setup guide
├── API-EXAMPLES.md                    # API usage examples
├── pom.xml                            # Maven configuration
├── docker-compose.yaml                # Docker setup
└── Dockerfile
```

### Key Files to Understand

| File | Purpose | When to Modify |
|------|---------|---------------|
| `rules/scoring-rules.yaml` | All scoring logic | Adding/changing scoring rules |
| `rules/eligibility-rules.yaml` | Loan eligibility criteria | Changing eligibility thresholds |
| `CreditScoringService.java` | Score calculation engine | Implementing rule evaluation logic |
| `CreditAssessmentService.java` | Main orchestration | Adding new assessment workflows |
| `RuleEngine.java` | YAML rule interpreter | Changing rule evaluation logic |
| `application.yml` | Configuration | Adding config properties |
| `CreditAssessmentController.java` | REST API | Adding new endpoints |

---

## Technology Stack

### Core Technologies

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 17+ | Primary language |
| **Spring Boot** | 3.2.0 | Application framework |
| **Spring Data JPA** | 3.2.0 | ORM / Database access |
| **PostgreSQL** | 14+ | Primary database |
| **H2** | Latest | Testing database |
| **Maven** | 3.8+ | Build tool |
| **Lombok** | Latest | Reduce boilerplate |
| **OpenPDF** | 1.3.30 | PDF report generation |
| **Jackson** | Latest | JSON processing |
| **SpringDoc OpenAPI** | 2.2.0 | API documentation (Swagger) |

### External Integrations

1. **UPI Platform**: Transaction data source (supports mock mode)
2. **LLM Providers** (Optional):
   - OpenAI GPT-4
   - FinGPT (HuggingFace)
   - Ollama (local)

### Build & Deployment

```bash
# Build
mvn clean package

# Run tests
mvn test

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=test

# Build Docker image
docker build -t opencredit-core .

# Run with Docker Compose
docker-compose up -d
```

---

## Architecture & Design

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         OpenCredit Platform                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                    API Layer                            │   │
│   │  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌──────┐  │   │
│   │  │Assess  │ │Insights│ │ Rules  │ │  Chat  │ │ Demo │  │   │
│   │  └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘ └──┬───┘  │   │
│   └──────┼──────────┼──────────┼──────────┼─────────┼──────┘   │
│          │          │          │          │         │          │
│   ┌──────▼──────────▼──────────▼──────────▼─────────▼──────┐   │
│   │                  Service Layer                         │   │
│   │  ┌────────────────────────────────────────────────┐    │   │
│   │  │      Credit Assessment Service                 │    │   │
│   │  └────────────────────┬───────────────────────────┘    │   │
│   │                       │                                │   │
│   │  ┌────────────────────▼───────────────────────────┐    │   │
│   │  │   🎯 RULE ENGINE (Source of Truth)            │    │   │
│   │  │   • scoring-rules.yaml                         │    │   │
│   │  │   • eligibility-rules.yaml                     │    │   │
│   │  │   • Deterministic decisions                    │    │   │
│   │  │   • 100% auditable                             │    │   │
│   │  └────────────────────┬───────────────────────────┘    │   │
│   │                       │                                │   │
│   │  ┌────────────────────▼───────────────────────────┐    │   │
│   │  │   🤖 LLM SERVICE (Explanations)               │    │   │
│   │  │   • Natural language explanations              │    │   │
│   │  │   • Personalized recommendations               │    │   │
│   │  │   • Does NOT make decisions                    │    │   │
│   │  └────────────────────────────────────────────────┘    │   │
│   └────────────────────────────────────────────────────────┘   │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│              PostgreSQL │ Redis │ UPI Platform                  │
└─────────────────────────────────────────────────────────────────┘
```

### Request Flow

#### 1. Credit Assessment Request

```
User Request
    │
    ▼
CreditAssessmentController.analyze(merchantId)
    │
    ▼
CreditAssessmentService.assessMerchant()
    │
    ├──► UpiPlatformClient.getTransactions()  // Fetch data
    │
    ├──► MetricsCalculationService.calculate()  // Calculate metrics
    │       ├─► Average monthly volume
    │       ├─► Bounce rate
    │       ├─► Growth rate
    │       ├─► Consistency (coefficient of variation)
    │       └─► Customer concentration
    │
    ├──► RuleEngine.loadRules()  // Load YAML rules
    │
    ├──► CreditScoringService.calculateScore()  // Apply rules
    │       ├─► Volume score (30% weight)
    │       ├─► Consistency score (25% weight)
    │       ├─► Growth score (15% weight)
    │       ├─► Bounce rate score (15% weight)
    │       └─► Concentration score (15% weight)
    │
    ├──► CreditScoringService.determineRiskCategory()  // Classify risk
    │
    ├──► CreditScoringService.checkEligibility()  // Check eligibility rules
    │
    └──► CreditAssessmentRepository.save()  // Persist results
         │
         ▼
    Return CreditAssessmentResponse
```

#### 2. Enhanced Assessment with LLM

```
User Request (Enhanced)
    │
    ▼
[Same as above until save]
    │
    ├──► CreditInsightsService.generateInsights()
    │       ├─► LlmClient.explainScore()  // Natural language
    │       ├─► LlmClient.analyzeRisk()   // Risk narrative
    │       └─► LlmClient.recommend()     // Improvement plan
    │
    └──► Return EnhancedCreditAssessmentResponse
```

### Component Responsibilities

#### Controllers (REST API Layer)

**Purpose**: Handle HTTP requests, validate inputs, return responses

**Rules**:
- Keep thin - no business logic
- Validate inputs using `@Valid`
- Return appropriate HTTP status codes
- Document with Swagger annotations (`@Operation`, `@ApiResponse`)

**Example**:
```java
@RestController
@RequestMapping("/credit-assessment")
public class CreditAssessmentController {

    @PostMapping("/analyze/{merchantId}")
    @Operation(summary = "Analyze merchant creditworthiness")
    public ResponseEntity<CreditAssessmentResponse> analyze(
            @PathVariable String merchantId) {
        // Delegate to service layer
        CreditAssessmentResponse response = assessmentService.assessMerchant(merchantId);
        return ResponseEntity.ok(response);
    }
}
```

#### Services (Business Logic Layer)

**Purpose**: Implement business logic, orchestrate operations

**Rules**:
- Transactional operations use `@Transactional`
- Handle exceptions appropriately
- Log important operations
- NO direct database access (use repositories)

**Key Services**:

1. **CreditAssessmentService**: Main orchestrator
   - Coordinates assessment workflow
   - Calls other services
   - Persists results

2. **CreditScoringService**: Score calculation
   - **MUST** use RuleEngine for all decisions
   - Load rules from YAML
   - Calculate component scores
   - Determine risk category
   - Check eligibility

3. **MetricsCalculationService**: Financial metrics
   - Calculate average monthly volume
   - Compute coefficient of variation
   - Analyze growth trends
   - Calculate bounce rate
   - Assess customer concentration

4. **CreditInsightsService**: LLM integration
   - Generate natural language explanations
   - Provide recommendations
   - Risk analysis narrative
   - **NEVER** makes scoring decisions

#### Rules Engine

**Location**: `src/main/java/com/lumexpay/vortexa/credit/rules/RuleEngine.java`

**Purpose**: Load and evaluate YAML rules

**Critical Methods**:
```java
public ScoringRules loadRules()  // Load from YAML
public int evaluateComponentScore(String component, double value)
public boolean checkEligibility(FinancialMetrics metrics)
public RiskCategory determineRiskCategory(int score)
public LoanParameters calculateLoanTerms(int score, double volume)
```

**Rules**:
- ALWAYS reload rules from YAML (unless cached)
- NEVER modify rules in code
- Log all rule evaluations for audit trail
- Handle missing/malformed YAML gracefully

#### Repositories (Data Access Layer)

**Purpose**: Database operations using JPA

**Rules**:
- Extend `JpaRepository<Entity, ID>`
- Use method name queries where possible
- Write custom `@Query` for complex queries
- NEVER write raw SQL (use JPQL)

**Example**:
```java
public interface CreditAssessmentRepository
        extends JpaRepository<CreditAssessment, UUID> {

    List<CreditAssessment> findByMerchantId(String merchantId);

    @Query("SELECT ca FROM CreditAssessment ca WHERE ca.creditScore >= :minScore")
    List<CreditAssessment> findByMinimumScore(@Param("minScore") int minScore);
}
```

### Data Models

#### Key Entities

1. **CreditAssessment** (JPA Entity)
   - Persisted assessment results
   - Contains score, risk category, loan terms
   - Immutable after creation (audit trail)

2. **Financial Metrics** (DTO)
   - Calculated from transactions
   - Input to scoring engine
   - Not persisted directly

3. **UpiTransaction** (DTO)
   - Transaction data from UPI platform
   - Used for metric calculation
   - Not persisted in OpenCredit DB

### Configuration Management

**File**: `src/main/resources/application.yml`

**Key Sections**:

1. **Database Configuration**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/credit_assessment_db
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
```

2. **Credit Assessment Configuration**
```yaml
credit:
  assessment:
    min-monthly-volume: 25000
    min-transaction-count: 20
    max-bounce-rate: 20
    weights:
      volume: 0.30
      consistency: 0.25
      growth: 0.15
      bounce-rate: 0.15
      concentration: 0.15
```

3. **LLM Configuration**
```yaml
llm:
  enabled: ${LLM_ENABLED:true}
  provider: ${LLM_PROVIDER:OPENAI}
  api:
    api-key: ${LLM_API_KEY:}
  model:
    openai-model: gpt-4o
    temperature: 0.7
```

4. **UPI Platform Configuration**
```yaml
upi:
  platform:
    base-url: ${UPI_PLATFORM_URL:http://localhost:8081}
    use-mock: ${UPI_USE_MOCK:true}  # Use mock data for testing
```

**Environment Variables** (for production):
- `DB_USERNAME`, `DB_PASSWORD`: Database credentials
- `UPI_PLATFORM_URL`, `UPI_PLATFORM_API_KEY`: UPI integration
- `LLM_ENABLED`, `LLM_PROVIDER`, `LLM_API_KEY`: LLM configuration
- `OPENAI_API_KEY`: OpenAI API key (if using OpenAI)

---

## Development Workflows

### Adding a New Scoring Component

**Scenario**: Add a "transaction frequency" component to the score

**Steps**:

1. **Update YAML Rules** (`rules/scoring-rules.yaml`):
```yaml
scoring:
  components:
    # ... existing components ...

    transaction_frequency:
      weight: 0.10  # Adjust other weights to sum to 1.0
      description: "Measures transaction frequency and business activity"
      metric: "avg_daily_transactions"
      tiers:
        - min: 10
          score: 100
          label: "Excellent"
        - min: 5
          score: 80
          label: "Good"
        - min: 2
          score: 60
          label: "Average"
        - min: 0
          score: 40
          label: "Low"
```

2. **Update ScoringRules Model** (if needed):
```java
// Add to ScoringRules.java
private ComponentRule transactionFrequency;
```

3. **Calculate Metric** (in `MetricsCalculationService`):
```java
public double calculateAvgDailyTransactions(List<UpiTransaction> transactions) {
    // Implementation
}
```

4. **Apply Rule** (in `CreditScoringService`):
```java
int frequencyScore = ruleEngine.evaluateComponentScore(
    "transaction_frequency",
    metrics.getAvgDailyTransactions()
);
```

5. **Update Tests**:
```java
@Test
public void testTransactionFrequencyScoring() {
    // Test logic
}
```

6. **Update Documentation**:
   - Update README.md with new component
   - Update API-EXAMPLES.md if response structure changed

7. **Version Bump**: Update version in `scoring-rules.yaml`

### Adding a New Eligibility Rule

**Scenario**: Add minimum unique customer count requirement

**Steps**:

1. **Update `rules/eligibility-rules.yaml`**:
```yaml
eligibility:
  rules:
    - id: "ELIG_006"
      name: "Minimum Unique Customers"
      description: "Ensures sufficient customer diversity"
      condition:
        metric: "unique_customer_count"
        operator: ">="
        value: 5
      failure_message: "Insufficient unique customers (minimum 5 required)"
      recommendation: "Expand customer base to demonstrate business viability"
```

2. **Update RuleEngine**:
```java
public boolean checkEligibility(FinancialMetrics metrics, ScoringRules rules) {
    for (EligibilityRule rule : rules.getEligibilityRules()) {
        if (!evaluateRule(rule, metrics)) {
            return false;  // All rules must pass
        }
    }
    return true;
}
```

3. **Update Tests**:
```java
@Test
public void testEligibility_MinimumCustomers_BelowThreshold() {
    metrics.setUniqueCustomerCount(3);
    boolean eligible = scoringService.checkEligibility(metrics);
    assertFalse(eligible);
}
```

### Adding a New API Endpoint

**Scenario**: Add endpoint to compare two merchants

**Steps**:

1. **Create DTO** (if needed):
```java
@Data
public class MerchantComparisonResponse {
    private CreditAssessmentResponse merchant1;
    private CreditAssessmentResponse merchant2;
    private String comparisonSummary;
}
```

2. **Add Service Method**:
```java
@Service
public class CreditAssessmentService {
    public MerchantComparisonResponse compareMerchants(
            String merchantId1, String merchantId2) {
        // Implementation
    }
}
```

3. **Add Controller Endpoint**:
```java
@GetMapping("/compare")
@Operation(summary = "Compare two merchants")
public ResponseEntity<MerchantComparisonResponse> compare(
        @RequestParam String merchant1,
        @RequestParam String merchant2) {
    return ResponseEntity.ok(
        assessmentService.compareMerchants(merchant1, merchant2)
    );
}
```

4. **Add Tests**:
```java
@Test
public void testCompareMerchantsEndpoint() throws Exception {
    mockMvc.perform(get("/credit-assessment/compare")
            .param("merchant1", "M1")
            .param("merchant2", "M2"))
        .andExpect(status().isOk());
}
```

5. **Document in API-EXAMPLES.md**:
```markdown
### Compare Merchants
```bash
curl -X GET "http://localhost:8080/api/v1/credit-assessment/compare?merchant1=M1&merchant2=M2"
```
```

### Adding LLM Features

**IMPORTANT**: LLM features are for explanation/UX only, NEVER for decisions

**Allowed LLM Use Cases**:
- Natural language explanations
- Personalized recommendations
- Risk narrative generation
- Chat interface
- Report enhancement

**Prohibited LLM Use Cases**:
- Score calculation
- Eligibility determination
- Risk category assignment
- Loan amount/term calculation

**Example - Adding Risk Narrative**:

1. **Add Service Method** (`CreditInsightsService`):
```java
public String generateRiskNarrative(CreditAssessmentResponse assessment) {
    String prompt = buildPrompt(assessment);
    return llmClient.generate(prompt);
}

private String buildPrompt(CreditAssessmentResponse assessment) {
    return String.format("""
        Generate a risk narrative for this merchant:
        Score: %d
        Risk: %s
        Volume: %.2f

        Explain the risk profile in 2-3 sentences.
        """, assessment.getCreditScore(),
             assessment.getRiskCategory(),
             assessment.getAverageMonthlyVolume());
}
```

2. **Add Controller Endpoint**:
```java
@GetMapping("/insights/{merchantId}/risk-narrative")
public ResponseEntity<String> getRiskNarrative(
        @PathVariable String merchantId) {
    CreditAssessmentResponse assessment =
        assessmentService.getLatestAssessment(merchantId);
    String narrative = insightsService.generateRiskNarrative(assessment);
    return ResponseEntity.ok(narrative);
}
```

3. **Validate Output**: Always validate LLM output doesn't contradict rules
```java
// Example validation
if (narrative.contains("credit score is")) {
    // Ensure score mentioned matches actual score
}
```

### Working with Mock Data

**For Testing Without UPI Platform**:

1. **Enable Mock Mode** (application.yml):
```yaml
upi:
  platform:
    use-mock: true
```

2. **Use Demo Controller**:
```bash
curl -X POST "http://localhost:8080/api/v1/demo/assess/excellent/DEMO_001"
```

3. **Available Scenarios** (see `MockTransactionDataProvider`):
   - `excellent`: High volume, consistent, low bounce
   - `good`: Solid metrics
   - `average`: Typical small merchant
   - `poor`: Struggling business
   - `ineligible`: Below thresholds
   - `seasonal`: High variation
   - `new`: Limited history

4. **Generate Custom Mock Data**:
```java
List<UpiTransaction> mockData = mockDataProvider.generateScenario(
    "custom",
    LocalDate.now().minusMonths(6),
    LocalDate.now()
);
```

---

## Coding Conventions

### Java Style

1. **Naming**:
   - Classes: `PascalCase` (e.g., `CreditScoringService`)
   - Methods: `camelCase` (e.g., `calculateScore()`)
   - Constants: `UPPER_SNAKE_CASE` (e.g., `MAX_SCORE`)
   - Package names: lowercase (e.g., `com.lumexpay.vortexa.credit`)

2. **Lombok Usage**:
   - Use `@Data` for DTOs
   - Use `@Slf4j` for logging
   - Use `@RequiredArgsConstructor` for DI
   - Avoid `@Builder` for entities (JPA issues)

3. **Dependency Injection**:
   - Constructor injection (preferred)
   - Final fields for dependencies
   - Use `@RequiredArgsConstructor` from Lombok

**Example**:
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditScoringService {
    private final RuleEngine ruleEngine;
    private final MetricsCalculationService metricsService;

    public int calculateScore(String merchantId) {
        log.debug("Calculating score for merchant: {}", merchantId);
        // Implementation
    }
}
```

4. **Exception Handling**:
   - Create custom exceptions for business logic
   - Use `@ControllerAdvice` for global handling
   - Include meaningful error messages
   - Log stack traces for unexpected errors

**Example**:
```java
public class MerchantNotFoundException extends RuntimeException {
    public MerchantNotFoundException(String merchantId) {
        super("Merchant not found: " + merchantId);
    }
}

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MerchantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            MerchantNotFoundException ex) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(ex.getMessage()));
    }
}
```

### YAML Rules Conventions

1. **Structure**:
   - Consistent indentation (2 spaces)
   - Comments for all rules
   - Version tracking
   - Changelog at bottom

2. **Documentation**:
   - Every rule has `description`
   - Complex rules have `explanation`
   - Include `recommendation` for failures

3. **Versioning**:
   - Semantic versioning: `MAJOR.MINOR.PATCH`
   - Major: Breaking changes to scoring
   - Minor: New rules/components
   - Patch: Bug fixes, clarifications

4. **IDs**:
   - Format: `PREFIX_NNN` (e.g., `ELIG_001`, `FRAUD_003`)
   - Sequential numbering
   - Never reuse IDs

### API Design

1. **Endpoint Naming**:
   - RESTful conventions
   - Plural nouns for collections
   - Kebab-case for multi-word endpoints

**Example**:
```
POST   /credit-assessment/analyze/{merchantId}
GET    /credit-assessment/report/{merchantId}
GET    /credit-assessment/score-range?minScore=60&maxScore=80
```

2. **Response Structure**:
   - Consistent field naming (camelCase)
   - Include metadata (timestamps, versions)
   - Error responses with `error`, `message`, `timestamp`

**Example Success Response**:
```json
{
  "assessmentId": "ASM-2025-001234",
  "merchantId": "MERCH001",
  "assessmentDate": "2025-01-15T10:30:00Z",
  "creditScore": 72,
  "riskCategory": "MEDIUM",
  "rulesVersion": "1.0.0"
}
```

**Example Error Response**:
```json
{
  "error": "MERCHANT_NOT_FOUND",
  "message": "Merchant not found: MERCH999",
  "timestamp": "2025-01-15T10:30:00Z",
  "path": "/credit-assessment/analyze/MERCH999"
}
```

3. **HTTP Status Codes**:
   - `200 OK`: Successful GET
   - `201 Created`: Successful POST creating resource
   - `400 Bad Request`: Invalid input
   - `404 Not Found`: Resource not found
   - `500 Internal Server Error`: Server errors

### Logging

1. **Levels**:
   - `ERROR`: Errors requiring immediate attention
   - `WARN`: Warnings, recoverable errors
   - `INFO`: Important business events
   - `DEBUG`: Detailed diagnostic information

2. **What to Log**:
   - **DO** log:
     - Assessment requests (merchantId, timestamp)
     - Score calculations (score, risk category)
     - Rule evaluations (rule ID, result)
     - Eligibility checks (pass/fail, reason)
     - External API calls (success/failure)
     - Configuration changes

   - **DON'T** log:
     - Sensitive PII
     - Full transaction details
     - API keys/secrets
     - Raw SQL queries (use DEBUG level only)

3. **Format**:
```java
log.info("Credit assessment completed - merchantId: {}, score: {}, risk: {}",
    merchantId, score, riskCategory);

log.error("Failed to fetch UPI transactions for merchant: {}", merchantId, exception);
```

### Comments & Documentation

1. **When to Comment**:
   - Complex algorithms
   - Non-obvious business logic
   - Regulatory compliance requirements
   - Workarounds for bugs
   - Public API methods

2. **When NOT to Comment**:
   - Obvious code
   - Self-explanatory method names
   - Redundant descriptions

**Good Example**:
```java
/**
 * Calculates the consistency score using coefficient of variation.
 *
 * CV = (Standard Deviation / Mean) × 100
 * Score = 100 - CV (capped at 0-100)
 *
 * Lower CV indicates more consistent revenue, resulting in higher score.
 * Seasonal businesses may get bonus adjustment (see scoring-rules.yaml).
 */
public int calculateConsistencyScore(List<Double> monthlyVolumes) {
    // Implementation
}
```

**Bad Example**:
```java
// Get the score
int score = getScore();  // ❌ Redundant
```

---

## Testing Strategy

### Test Structure

```
src/test/java/
└── com/lumexpay/vortexa/credit/
    ├── service/
    │   ├── CreditScoringServiceTest.java      # Unit tests
    │   └── CreditAssessmentServiceTest.java
    ├── controller/
    │   └── CreditAssessmentControllerTest.java  # API tests
    ├── rules/
    │   └── RuleEngineTest.java                 # Rules validation
    └── integration/
        └── CreditAssessmentIntegrationTest.java  # E2E tests
```

### Unit Testing

**Guidelines**:
- Test one component in isolation
- Mock dependencies
- Cover edge cases
- Aim for 80%+ coverage

**Example**:
```java
@ExtendWith(MockitoExtension.class)
class CreditScoringServiceTest {

    @Mock
    private RuleEngine ruleEngine;

    @Mock
    private MetricsCalculationService metricsService;

    @InjectMocks
    private CreditScoringService scoringService;

    @Test
    void testCalculateScore_HighVolume_LowRisk() {
        // Arrange
        FinancialMetrics metrics = new FinancialMetrics();
        metrics.setAverageMonthlyVolume(500000);
        metrics.setBounceRate(2.5);

        ScoringRules rules = new ScoringRules();
        when(ruleEngine.loadRules()).thenReturn(rules);

        // Act
        int score = scoringService.calculateScore(metrics);

        // Assert
        assertTrue(score >= 80, "High volume should result in low risk score");
    }

    @Test
    void testCheckEligibility_BelowMinimumVolume_Ineligible() {
        // Test logic
    }
}
```

### Integration Testing

**Guidelines**:
- Test full request-response cycle
- Use `@SpringBootTest`
- Use test database (H2)
- Test actual API endpoints

**Example**:
```java
@SpringBootTest
@AutoConfigureMockMvc
class CreditAssessmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testAnalyzeMerchant_Returns200() throws Exception {
        mockMvc.perform(post("/credit-assessment/analyze/TEST_MERCHANT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.creditScore").exists())
            .andExpect(jsonPath("$.riskCategory").exists());
    }
}
```

### Rules Testing

**Critical**: Test that YAML rules are correctly loaded and evaluated

**Example**:
```java
@Test
void testRulesLoading_ValidYaml() {
    ScoringRules rules = ruleEngine.loadRules();

    assertNotNull(rules);
    assertEquals("1.0.0", rules.getVersion());
    assertEquals(5, rules.getComponents().size());
}

@Test
void testVolumeScoring_Thresholds() {
    // Test 500K+ -> 100 points
    assertEquals(100, ruleEngine.evaluateVolumeScore(500000));

    // Test 200K-500K -> 80 points
    assertEquals(80, ruleEngine.evaluateVolumeScore(300000));

    // Test edge cases
    assertEquals(100, ruleEngine.evaluateVolumeScore(500000));
    assertEquals(80, ruleEngine.evaluateVolumeScore(499999));
}
```

### Test Coverage

**Minimum Requirements**:
- Overall coverage: 80%
- Service layer: 90%+
- Rules engine: 95%+
- Controllers: 70%+

**Run Coverage Report**:
```bash
mvn test jacoco:report

# View report at: target/site/jacoco/index.html
```

### Manual Testing

**Swagger UI**: `http://localhost:8080/swagger-ui.html`

**Quick Test Sequence**:
```bash
# 1. Health check
curl http://localhost:8080/actuator/health

# 2. View rules
curl http://localhost:8080/api/v1/rules/methodology

# 3. Demo assessment
curl -X POST "http://localhost:8080/api/v1/demo/assess/excellent/DEMO_001"

# 4. Retrieve assessment
curl http://localhost:8080/api/v1/credit-assessment/report/DEMO_001

# 5. Get insights (if LLM enabled)
curl http://localhost:8080/api/v1/insights/DEMO_001/narrative
```

---

## Common Tasks

### Task 1: Update Scoring Weights

**When**: Changing importance of scoring components

**Steps**:
1. Update `rules/scoring-rules.yaml`:
```yaml
scoring:
  components:
    volume:
      weight: 0.35  # Changed from 0.30
    consistency:
      weight: 0.25  # Unchanged
    # ... adjust others to sum to 1.0
```

2. Update version and changelog:
```yaml
version: "1.1.0"
changelog:
  - version: "1.1.0"
    date: "2025-01-15"
    changes:
      - "Increased volume weight from 30% to 35%"
      - "Decreased bounce rate weight from 15% to 10%"
```

3. Run impact analysis:
```bash
# Test with existing data to see score changes
mvn test -Dtest=RuleChangeImpactTest
```

4. Update documentation (README.md)

### Task 2: Add Fraud Detection Rule

**When**: Identifying new fraud pattern

**Steps**:
1. Add to `rules/scoring-rules.yaml`:
```yaml
fraud_detection:
  rules:
    - id: "FRAUD_004"
      name: "Circular Transaction Pattern"
      description: "Detects suspicious circular money flow"
      condition:
        metric: "circular_transaction_ratio"
        operator: ">"
        value: 30  # More than 30% circular
      severity: "HIGH"
      action: "MANUAL_REVIEW"
      explanation: |
        Detected potential circular transactions where money
        flows back to sender. This may indicate artificial
        transaction volume inflation.
```

2. Implement metric calculation:
```java
// In MetricsCalculationService
public double calculateCircularTransactionRatio(
        List<UpiTransaction> transactions) {
    // Implementation to detect circular patterns
}
```

3. Apply in fraud check:
```java
// In CreditScoringService
public List<FraudIndicator> checkFraudIndicators(
        FinancialMetrics metrics) {
    List<FraudIndicator> indicators = new ArrayList<>();

    for (FraudRule rule : rules.getFraudRules()) {
        if (evaluateFraudRule(rule, metrics)) {
            indicators.add(new FraudIndicator(rule));
        }
    }

    return indicators;
}
```

4. Test thoroughly:
```java
@Test
void testFraudDetection_CircularTransactions() {
    metrics.setCircularTransactionRatio(35.0);
    List<FraudIndicator> indicators =
        scoringService.checkFraudIndicators(metrics);

    assertTrue(indicators.stream()
        .anyMatch(i -> i.getRuleId().equals("FRAUD_004")));
}
```

### Task 3: Configure LLM Provider

**Scenario 1: Use OpenAI**

1. Set environment variable:
```bash
export OPENAI_API_KEY="sk-..."
```

2. Update `application.yml`:
```yaml
llm:
  enabled: true
  provider: OPENAI
  model:
    openai-model: gpt-4o
    temperature: 0.7
```

3. Test:
```bash
curl http://localhost:8080/api/v1/insights/DEMO_001/narrative
```

**Scenario 2: Use Ollama (Local)**

1. Start Ollama:
```bash
ollama serve
ollama pull llama3:8b
```

2. Update `application.yml`:
```yaml
llm:
  enabled: true
  provider: OLLAMA
  api:
    base-url: http://localhost:11434
  model:
    ollama-model: llama3:8b
```

3. Test as above

**Scenario 3: Disable LLM**

```yaml
llm:
  enabled: false
```

System will work without LLM features (explanations, chat, recommendations).

### Task 4: Generate PDF Report

**Using Service**:
```java
@Autowired
private PdfReportService pdfReportService;

public void generateReport(String merchantId) {
    CreditAssessmentResponse assessment =
        assessmentService.getLatestAssessment(merchantId);

    byte[] pdfBytes = pdfReportService.generateReport(assessment);

    // Save or return
}
```

**Using API**:
```bash
curl -o report.pdf \
  http://localhost:8080/api/v1/credit-assessment/report/MERCH001/pdf
```

### Task 5: Schedule Periodic Reassessment

**Configuration** (`application.yml`):
```yaml
scheduler:
  reassessment:
    enabled: true
    cron: "0 0 2 1 * ?"  # 2 AM on 1st of every month
```

**Implementation** (`CreditReassessmentScheduler`):
```java
@Component
@EnableScheduling
@ConditionalOnProperty(name = "scheduler.reassessment.enabled", havingValue = "true")
public class CreditReassessmentScheduler {

    @Scheduled(cron = "${scheduler.reassessment.cron}")
    public void reassessAllMerchants() {
        log.info("Starting scheduled credit reassessment");

        List<String> merchantIds = getActiveMerchants();

        for (String merchantId : merchantIds) {
            try {
                assessmentService.reassessMerchant(merchantId);
            } catch (Exception e) {
                log.error("Reassessment failed for: {}", merchantId, e);
            }
        }

        log.info("Completed scheduled reassessment for {} merchants",
            merchantIds.size());
    }
}
```

---

## Troubleshooting

### Issue: Database Connection Refused

**Symptoms**:
```
java.net.ConnectException: Connection refused
```

**Solutions**:
1. Check PostgreSQL is running:
```bash
docker ps | grep postgres
# or
sudo systemctl status postgresql
```

2. Verify connection details in `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/credit_assessment_db
    username: postgres
    password: postgres
```

3. Test connection:
```bash
psql -h localhost -U postgres -d credit_assessment_db
```

4. For Docker:
```bash
docker-compose up -d postgres
docker logs postgres  # Check for errors
```

### Issue: Rules YAML Not Loading

**Symptoms**:
```
FileNotFoundException: rules/scoring-rules.yaml
```

**Solutions**:
1. Verify file exists:
```bash
ls -la src/main/resources/rules/
```

2. Check classpath configuration (in `application.yml`):
```yaml
rules:
  path: rules/scoring-rules.yaml  # Relative to classpath
```

3. Verify YAML syntax:
```bash
# Use online YAML validator or:
python -c "import yaml; yaml.safe_load(open('src/main/resources/rules/scoring-rules.yaml'))"
```

4. Check for BOM or encoding issues:
```bash
file src/main/resources/rules/scoring-rules.yaml
# Should show: ASCII text or UTF-8 text
```

### Issue: LLM API Timeouts

**Symptoms**:
```
SocketTimeoutException: Read timed out
```

**Solutions**:
1. Increase timeout in `application.yml`:
```yaml
llm:
  api:
    timeout-seconds: 120  # Increased from 60
```

2. Check API key is valid:
```bash
curl https://api.openai.com/v1/models \
  -H "Authorization: Bearer $OPENAI_API_KEY"
```

3. Disable LLM if not critical:
```yaml
llm:
  enabled: false
```

4. Use local LLM (Ollama) instead:
```yaml
llm:
  provider: OLLAMA
  api:
    base-url: http://localhost:11434
```

### Issue: Score Calculation Incorrect

**Symptoms**: Score doesn't match expected value

**Debug Steps**:

1. Enable debug logging:
```yaml
logging:
  level:
    com.lumexpay.vortexa.credit: DEBUG
```

2. Check component scores:
```java
log.debug("Component scores: volume={}, consistency={}, growth={}, bounce={}, concentration={}",
    volumeScore, consistencyScore, growthScore, bounceScore, concentrationScore);
```

3. Verify weights sum to 1.0:
```java
double totalWeight = weights.values().stream()
    .mapToDouble(Double::doubleValue)
    .sum();
assert Math.abs(totalWeight - 1.0) < 0.001 : "Weights must sum to 1.0";
```

4. Test with known data:
```java
@Test
void testScoreCalculation_KnownValues() {
    // Use fixed metrics with known expected score
    FinancialMetrics metrics = createTestMetrics();
    int score = scoringService.calculateScore(metrics);
    assertEquals(72, score, "Score should be 72 for test metrics");
}
```

5. Validate YAML rules:
```java
@Test
void testRulesIntegrity() {
    ScoringRules rules = ruleEngine.loadRules();

    // Check all tiers are properly ordered
    for (ComponentRule component : rules.getComponents()) {
        List<Tier> tiers = component.getTiers();
        for (int i = 0; i < tiers.size() - 1; i++) {
            assertTrue(tiers.get(i).getMin() >= tiers.get(i + 1).getMax(),
                "Tiers must be properly ordered");
        }
    }
}
```

### Issue: Test Failures After Rule Changes

**Symptoms**: Tests fail after modifying `scoring-rules.yaml`

**Solutions**:
1. Update test expectations:
```java
// Old expectation based on v1.0.0 rules
assertEquals(80, score);

// Update to match v1.1.0 rules
assertEquals(85, score);  // Score increased due to weight change
```

2. Use dynamic assertions based on rules:
```java
ScoringRules rules = ruleEngine.loadRules();
int expectedScore = calculateExpectedScore(metrics, rules);
assertEquals(expectedScore, actualScore);
```

3. Test rule versions separately:
```java
@Test
void testScoring_V1_0_0_Rules() {
    ScoringRules rules = loadRulesVersion("1.0.0");
    // Test with v1.0.0 expectations
}

@Test
void testScoring_V1_1_0_Rules() {
    ScoringRules rules = loadRulesVersion("1.1.0");
    // Test with v1.1.0 expectations
}
```

### Issue: UPI Platform Integration Failures

**Symptoms**:
```
HttpClientErrorException: 401 Unauthorized
```

**Solutions**:
1. Enable mock mode for testing:
```yaml
upi:
  platform:
    use-mock: true
```

2. Check API key:
```yaml
upi:
  platform:
    api-key: ${UPI_PLATFORM_API_KEY}
```

3. Verify endpoint:
```bash
curl -H "X-API-Key: your-key" \
  http://upi-platform/api/transactions?merchantId=TEST
```

4. Implement fallback:
```java
@Service
public class UpiPlatformClient {

    @Value("${upi.platform.fallback-to-mock}")
    private boolean fallbackToMock;

    public List<UpiTransaction> getTransactions(String merchantId) {
        try {
            return fetchFromPlatform(merchantId);
        } catch (Exception e) {
            if (fallbackToMock) {
                log.warn("UPI platform unavailable, using mock data");
                return mockDataProvider.generate(merchantId);
            }
            throw e;
        }
    }
}
```

### Issue: Performance Degradation

**Symptoms**: Slow API responses

**Debug Steps**:

1. Enable SQL logging:
```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
```

2. Check for N+1 queries:
```java
// Bad - N+1 problem
for (Assessment a : assessments) {
    a.getMerchant().getName();  // Triggers query each iteration
}

// Good - Use JOIN FETCH
@Query("SELECT a FROM Assessment a JOIN FETCH a.merchant")
List<Assessment> findAllWithMerchant();
```

3. Add database indexes:
```sql
CREATE INDEX idx_assessment_merchant ON credit_assessments(merchant_id);
CREATE INDEX idx_assessment_date ON credit_assessments(assessment_date);
```

4. Cache rules:
```java
@Service
public class RuleEngine {

    @Cacheable("scoring-rules")
    public ScoringRules loadRules() {
        // Load from YAML
    }
}
```

5. Profile application:
```bash
# Use JProfiler, YourKit, or Java Flight Recorder
java -XX:StartFlightRecording=duration=60s,filename=profile.jfr -jar app.jar
```

---

## Deployment Checklist

### Pre-Deployment

- [ ] All tests passing (`mvn test`)
- [ ] Code coverage meets minimum (80%)
- [ ] Rules YAML validated
- [ ] Documentation updated
- [ ] Changelog updated
- [ ] Version bumped (if needed)
- [ ] Security scan completed
- [ ] Performance tested

### Configuration

- [ ] Database credentials set
- [ ] UPI platform configured
- [ ] LLM provider configured (if enabled)
- [ ] File storage paths set
- [ ] Logging configured
- [ ] Monitoring enabled

### Post-Deployment

- [ ] Health check passes
- [ ] Database migrations applied
- [ ] Sample assessment works
- [ ] PDF generation works
- [ ] API documentation accessible
- [ ] Monitoring dashboards configured
- [ ] Alerts configured
- [ ] Backup strategy verified

---

## Additional Resources

### Internal Documentation

- **README.md**: Project overview and setup
- **QUICKSTART.md**: Quick start guide
- **API-EXAMPLES.md**: API usage examples
- **docs/RULE_VS_LLM_DECISION_GUIDE.md**: Critical architecture decision
- **docs/FINE_TUNING_GUIDE.md**: LLM fine-tuning guide
- **rules/README.md**: Rules documentation
- **rules/CONTRIBUTING.md**: How to contribute rules

### External Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [OpenAPI Specification](https://swagger.io/specification/)
- [RBI Guidelines for NBFCs](https://www.rbi.org.in)

### API Documentation

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/api-docs`

### Support Channels

- **GitHub Issues**: Report bugs and feature requests
- **Email**: support@opencredit.org.in
- **Discord**: OpenCredit Community

---

## Quick Reference

### Essential Commands

```bash
# Build
mvn clean package

# Run
mvn spring-boot:run

# Test
mvn test

# Coverage
mvn test jacoco:report

# Docker
docker-compose up -d

# Logs
docker-compose logs -f opencredit
```

### Key Files Quick Access

```bash
# Rules
vim src/main/resources/rules/scoring-rules.yaml
vim src/main/resources/rules/eligibility-rules.yaml

# Configuration
vim src/main/resources/application.yml

# Main service
vim src/main/java/com/lumexpay/vortexa/credit/service/CreditScoringService.java

# Rules engine
vim src/main/java/com/lumexpay/vortexa/credit/rules/RuleEngine.java
```

### Common API Calls

```bash
# Health check
curl http://localhost:8080/actuator/health

# View rules
curl http://localhost:8080/api/v1/rules/methodology

# Assess merchant
curl -X POST http://localhost:8080/api/v1/credit-assessment/analyze/MERCH001

# Get report
curl http://localhost:8080/api/v1/credit-assessment/report/MERCH001

# Demo mode
curl -X POST http://localhost:8080/api/v1/demo/assess/excellent/DEMO_001
```

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-01-15 | Initial CLAUDE.md created |

---

## Feedback

This document is meant to evolve. If you find:
- Missing information
- Unclear explanations
- Outdated instructions
- Errors or inconsistencies

Please open an issue or submit a PR!

---

**Remember**: When in doubt, prioritize transparency, fairness, and regulatory compliance. The rules engine is the source of truth for all credit decisions. LLM is for user experience enhancement only.
