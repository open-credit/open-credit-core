# OpenCredit Rules

## Fair, Transparent Credit Scoring for Everyone

[![License: LGPL v3](https://img.shields.io/badge/License-LGPL%20v3-blue.svg)](https://www.gnu.org/licenses/lgpl-3.0)
[![Contributions Welcome](https://img.shields.io/badge/contributions-welcome-brightgreen.svg)](CONTRIBUTING.md)
[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](./scoring-rules.yaml)

---

## 🎯 What is OpenCredit?

OpenCredit is an **open-source credit scoring framework** designed to bring fair, transparent credit access to millions of small businesses, entrepreneurs, and merchants who have been traditionally excluded from formal credit systems.

### The Problem We're Solving

Traditional credit scores:
- ❌ Are black boxes (nobody knows how they work)
- ❌ Exclude people without formal credit history
- ❌ Can embed biases without anyone knowing
- ❌ Are controlled by private companies

### Our Solution

OpenCredit rules are:
- ✅ **Transparent** - Every rule is visible and explained
- ✅ **Fair** - No discriminatory factors, evidence-based decisions
- ✅ **Inclusive** - Designed for underserved populations
- ✅ **Community-Governed** - Anyone can propose improvements
- ✅ **Auditable** - Full version history of all changes

---

## 📊 How Scoring Works

### The Five Pillars of Credit Assessment

Your OpenCredit score (0-100) is based on five factors:

```
┌────────────────────────────────────────────────────────────────┐
│                    OPENCREDIT SCORE (0-100)                    │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐           │
│   │   VOLUME    │  │ CONSISTENCY │  │   GROWTH    │           │
│   │    30%      │  │     25%     │  │     15%     │           │
│   │             │  │             │  │             │           │
│   │  Business   │  │   Stable    │  │  Improving  │           │
│   │  Activity   │  │   Income    │  │  Trajectory │           │
│   └─────────────┘  └─────────────┘  └─────────────┘           │
│                                                                │
│   ┌─────────────┐  ┌─────────────┐                            │
│   │ BOUNCE RATE │  │CONCENTRATION│                            │
│   │     15%     │  │     15%     │                            │
│   │             │  │             │                            │
│   │  Payment    │  │  Customer   │                            │
│   │  Success    │  │  Diversity  │                            │
│   └─────────────┘  └─────────────┘                            │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### Score Breakdown

| Component | Weight | What It Measures | Why It Matters |
|-----------|--------|------------------|----------------|
| **Volume** | 30% | Monthly transaction amount | Shows business activity and repayment capacity |
| **Consistency** | 25% | Stable monthly income | Predictable cash flow for EMI payments |
| **Growth** | 15% | Business trajectory | Growing businesses have better future capacity |
| **Bounce Rate** | 15% | Payment success rate | Low failures indicate financial health |
| **Concentration** | 15% | Customer diversity | Reduces dependency risk |

### Risk Categories

| Score | Category | What It Means |
|-------|----------|---------------|
| 80-100 | 🟢 **LOW RISK** | Excellent profile, best loan terms |
| 60-79 | 🟡 **MEDIUM RISK** | Good profile, standard terms |
| 0-59 | 🔴 **HIGH RISK** | Needs improvement, limited options |

---

## 🚫 What We DON'T Use

**OpenCredit explicitly NEVER uses these factors:**

- ❌ Gender
- ❌ Religion
- ❌ Caste
- ❌ Geographic discrimination
- ❌ Family background
- ❌ Education level
- ❌ Social media activity
- ❌ Personal characteristics

**We only use financial transaction behavior.**

---

## 📁 Repository Structure

```
rules/
├── scoring-rules.yaml      # Main scoring methodology
├── eligibility-rules.yaml  # Minimum requirements for loans
├── CONTRIBUTING.md         # How to propose changes
├── CHANGELOG.md           # History of rule changes
└── README.md              # This file
```

---

## 🔍 Understanding the Rules

### Scoring Rules (`scoring-rules.yaml`)

This file defines:
- Component weights (how much each factor matters)
- Scoring tiers (what values get what scores)
- Risk classifications (how scores map to risk)
- Loan parameters (amount, tenure, rates by risk)

**Example: Volume Scoring**

```yaml
volume:
  weight: 0.30  # 30% of final score
  tiers:
    - min: 500000      # ₹5 lakh+
      score: 100       # Excellent
    - min: 200000      # ₹2-5 lakh  
      score: 80        # Good
    - min: 100000      # ₹1-2 lakh
      score: 60        # Average
    - min: 50000       # ₹50K-1 lakh
      score: 40        # Below Average
    - min: 25000       # ₹25-50K
      score: 25        # Low
    - min: 0           # Below ₹25K
      score: 10        # Minimal
```

### Eligibility Rules (`eligibility-rules.yaml`)

This file defines minimum requirements:
- Minimum transaction volume
- Minimum transaction count
- Maximum allowed bounce rate
- Minimum business tenure
- Fraud indicators

**Example: Minimum Volume Rule**

```yaml
- id: "ELIG_MIN_VOLUME"
  name: "Minimum Monthly Volume"
  
  description: |
    Ensures minimum business activity for loan repayment.
    Set intentionally LOW to include micro-merchants.
  
  condition:
    field: "average_monthly_volume"
    operator: ">="
    value: 25000  # ₹25,000
  
  failure_message: "Volume below ₹25,000 minimum"
  
  recommendations:
    - "Increase digital payment acceptance"
    - "Encourage customers to pay via UPI"
```

---

## 🤝 Contributing

We welcome contributions from:
- **Economists** - Help us improve fairness
- **Data Scientists** - Improve accuracy with research
- **Social Workers** - Advocate for underserved populations
- **Developers** - Implement rule engine improvements
- **Merchants** - Share real-world feedback
- **Anyone** - Every perspective matters!

### How to Contribute

1. **Read** the [Contributing Guide](CONTRIBUTING.md)
2. **Open an Issue** with your proposal
3. **Discuss** with the community
4. **Submit** a Pull Request
5. **Celebrate** making credit fairer! 🎉

### Contribution Examples

- "The ₹25K threshold excludes rural merchants - propose ₹15K"
- "Festival seasonality needs better handling"
- "Add documentation in Hindi for wider access"
- "Research showing X factor improves prediction"

---

## 📈 Impact

OpenCredit rules are designed to help:

| Who | How |
|-----|-----|
| Street vendors | Build credit through UPI transactions |
| Kirana stores | Access working capital loans |
| Solo entrepreneurs | Prove creditworthiness without formal records |
| Women-owned businesses | Equal assessment without gender bias |
| Rural merchants | Fair treatment regardless of location |
| First-time borrowers | Alternative to traditional credit history |

---

## 🔄 Versioning

Rules use semantic versioning: `MAJOR.MINOR.PATCH`

- **MAJOR**: Significant methodology changes
- **MINOR**: New rules or threshold changes
- **PATCH**: Documentation, bug fixes

Current Version: **1.0.0**

---

## 📜 License

OpenCredit Rules are licensed under **LGPL v3**.

This means:
- ✅ Free to use in any application
- ✅ Free to modify and distribute
- ✅ Modifications to rules must be open-sourced
- ✅ Your application code can remain private

---

## 🌍 Community

- **GitHub Discussions**: Ask questions, share ideas
- **Monthly Calls**: Community video calls (schedule TBD)
- **Twitter**: [@OpenCreditOrg](https://twitter.com/OpenCreditOrg)
- **Email**: community@opencredit.org

---

## 🙏 Acknowledgments

OpenCredit is built by and for the community. Special thanks to:

- Early contributors and testers
- Microfinance experts who reviewed our methodology
- Merchant communities who provided feedback
- Everyone fighting for financial inclusion

---

## ⭐ Support

If you believe in fair credit:

1. **Star** this repository
2. **Share** with others
3. **Contribute** improvements
4. **Use** in your applications
5. **Advocate** for transparent credit

---

**Together, let's make credit fair for everyone.** 🌟

---

*"The best way to predict the future is to create it."* - Peter Drucker

*Let's create a future where credit is a right, not a privilege.*
