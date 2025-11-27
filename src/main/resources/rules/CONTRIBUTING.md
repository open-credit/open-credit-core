# Contributing to OpenCredit Rules

## 🌟 Welcome!

Thank you for your interest in contributing to OpenCredit! This project aims to bring **fair, transparent credit scoring** to millions of small businesses and entrepreneurs who have been traditionally excluded from formal credit systems.

Your contribution can directly impact the financial lives of:
- Street vendors
- Kirana store owners
- Solo entrepreneurs
- Small manufacturers
- Service providers
- And millions more...

## 🎯 Our Mission

> **Make credit scoring transparent, fair, and community-governed.**

Traditional credit scores are black boxes. OpenCredit is different:
- Every rule is visible and explained
- Anyone can propose changes
- Decisions are evidence-based
- Community consensus drives evolution

## 📋 Types of Contributions

### 1. Rule Improvements
Propose changes to scoring rules, thresholds, or weights.

**Examples:**
- "Volume threshold of ₹25K is too high for rural merchants"
- "Seasonal businesses need better handling"
- "Add tier for merchants with 50-100 transactions"

### 2. New Rules
Suggest new rules that improve fairness or accuracy.

**Examples:**
- "Detect circular transaction patterns"
- "Account for festival seasonality"
- "Consider business vintage in scoring"

### 3. Documentation
Improve explanations so non-technical users understand the rules.

### 4. Research & Evidence
Provide data or research supporting rule changes.

### 5. Translations
Translate rules documentation into regional languages.

## 🔄 Contribution Process

### Step 1: Open an Issue
Before making changes, open an issue to discuss:

```markdown
## Rule Change Proposal

**Current Rule:** [What rule you want to change]
**Proposed Change:** [What you want to change it to]
**Rationale:** [Why this change improves fairness/accuracy]
**Evidence:** [Data, research, or examples supporting this]
**Impact:** [Who is affected and how]
```

### Step 2: Community Discussion
- Other contributors will review and discuss
- Aim for consensus (not just majority)
- Consider all perspectives, especially affected users

### Step 3: Submit Pull Request
Once there's agreement:

1. Fork the repository
2. Create a branch: `git checkout -b improve-volume-threshold`
3. Make your changes to YAML files
4. Update CHANGELOG
5. Submit PR with detailed description

### Step 4: Review & Merge
- At least 2 maintainers must approve
- Changes must include impact analysis
- No changes that harm existing borrowers retroactively

## 📝 Rule Writing Guidelines

### YAML Structure

```yaml
# Good: Clear, documented rule
- id: "ELIG_MIN_VOLUME"
  name: "Minimum Monthly Volume"
  version: "1.1"  # Increment when changed
  enabled: true
  
  description: |
    Ensures minimum business activity for loan repayment.
    Threshold set low to include micro-merchants.
  
  rationale: |
    Research shows merchants below ₹25K have 40% default rate.
    This protects both lenders and borrowers.
  
  condition:
    field: "average_monthly_volume"
    operator: "GREATER_THAN_OR_EQUAL"
    value: 25000
    unit: "INR"
  
  on_failure:
    reason_code: "VOLUME_TOO_LOW"
    message: "Volume below ₹25,000 minimum"
    recommendations:
      - "Increase digital payment acceptance"
```

### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Rule IDs | UPPERCASE_SNAKE | `ELIG_MIN_VOLUME` |
| Field names | snake_case | `average_monthly_volume` |
| Descriptions | Full sentences | "Ensures minimum..." |

### Documentation Requirements

Every rule must have:
- ✅ Clear description (what it does)
- ✅ Rationale (why it exists)
- ✅ Impact statement (who it affects)
- ✅ Failure message (user-friendly)
- ✅ Recommendations (how to improve)

## ⚖️ Fairness Principles

All contributions must adhere to these principles:

### 1. No Discriminatory Factors
Never use:
- Gender
- Religion
- Caste
- Geographic discrimination
- Family background
- Education level (irrelevant to creditworthiness)

### 2. Evidence-Based
- Rules should be based on data
- Avoid assumptions without evidence
- Document research supporting changes

### 3. Inclusive by Default
- Set thresholds as LOW as safely possible
- Consider edge cases (seasonal, new businesses)
- Provide paths to eligibility

### 4. Transparent
- Every rule must be explainable
- No "black box" calculations
- Document all formulas

### 5. Non-Retroactive
- Changes should not harm existing borrowers
- Grandfather existing assessments when appropriate

## 🧪 Testing Your Changes

### Local Testing

```bash
# Run rule validation
./validate-rules.sh

# Test against sample data
./test-rules.sh --input samples/merchants.json

# Check impact on existing scores
./impact-analysis.sh --rules new-rules.yaml
```

### Impact Analysis Requirements

For rule changes affecting scores:
1. Run analysis on sample dataset (provided)
2. Report: How many merchants affected?
3. Report: Direction of impact (higher/lower scores)
4. Report: Edge cases identified

## 📊 Evidence Standards

When proposing changes, provide:

### Tier 1: Strong Evidence
- Published research
- Large-scale data analysis
- Regulatory guidance

### Tier 2: Moderate Evidence
- Case studies
- Expert interviews
- Smaller data samples

### Tier 3: Supporting Evidence
- Anecdotal reports
- Logic-based arguments
- Comparative analysis

Tier 1 evidence can justify immediate changes.
Tier 2-3 evidence should be discussed before implementation.

## 🗣️ Communication

### Discussion Channels
- **GitHub Issues:** Rule proposals, bug reports
- **GitHub Discussions:** General questions, ideas
- **Community Calls:** Monthly video calls (see schedule)

### Code of Conduct
- Be respectful and inclusive
- Focus on ideas, not people
- Assume good intentions
- Welcome newcomers

## 🏆 Recognition

Contributors are recognized in:
- CONTRIBUTORS.md file
- Release notes
- Annual community report

Significant contributors may be invited to:
- Maintainer role
- Governance committee
- Speaking opportunities

## ❓ FAQ

### Q: Can I propose removing a rule?
A: Yes, with strong justification. Rules protecting borrowers are harder to remove.

### Q: How long does review take?
A: Simple changes: 1-2 weeks. Significant changes: 4-6 weeks (for community input).

### Q: What if I disagree with a decision?
A: Appeal process available. Escalate to governance committee if needed.

### Q: Can businesses propose rules?
A: Yes, but must disclose affiliation. Community evaluates independently.

## 🔗 Resources

- [Rule Format Specification](./docs/RULE_FORMAT.md)
- [Scoring Methodology](./docs/METHODOLOGY.md)
- [Impact Analysis Guide](./docs/IMPACT_ANALYSIS.md)
- [Research Bibliography](./docs/RESEARCH.md)

## 📞 Contact

- **Email:** contribute@opencredit.org
- **GitHub:** @opencredit/rules
- **Twitter:** @OpenCreditOrg

---

**Together, we can make credit fair for everyone.** 🙏

*Thank you for contributing to financial inclusion!*
