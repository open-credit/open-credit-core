-- =============================================
-- Credit Assessment Engine Database Schema
-- PostgreSQL
-- =============================================

-- Create database (run as superuser)
-- CREATE DATABASE credit_assessment_db;

-- Credit Assessments main table
CREATE TABLE IF NOT EXISTS credit_assessments (
    assessment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id VARCHAR(100) NOT NULL,
    assessment_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Financial Metrics
    last3_months_volume DECIMAL(15, 2),
    last6_months_volume DECIMAL(15, 2),
    last12_months_volume DECIMAL(15, 2),
    average_monthly_volume DECIMAL(15, 2),
    average_transaction_value DECIMAL(15, 2),
    transaction_count INTEGER,
    unique_customer_count INTEGER,
    
    -- Performance Metrics
    consistency_score DECIMAL(5, 2),
    growth_rate DECIMAL(8, 4),
    bounce_rate DECIMAL(5, 2),
    customer_concentration DECIMAL(5, 2),
    
    -- Component Scores
    volume_score DECIMAL(5, 2),
    growth_score DECIMAL(5, 2),
    bounce_rate_score DECIMAL(5, 2),
    concentration_score DECIMAL(5, 2),
    
    -- Credit Score
    credit_score INTEGER NOT NULL,
    risk_category VARCHAR(20) NOT NULL,
    
    -- Eligibility
    eligible_loan_amount DECIMAL(15, 2),
    max_tenure_days INTEGER,
    recommended_interest_rate DECIMAL(5, 2),
    is_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    ineligibility_reason VARCHAR(500),
    
    -- Report
    report_url VARCHAR(500),
    report_file_name VARCHAR(100),
    
    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    
    -- Constraints
    CONSTRAINT chk_credit_score CHECK (credit_score >= 0 AND credit_score <= 100),
    CONSTRAINT chk_risk_category CHECK (risk_category IN ('LOW', 'MEDIUM', 'HIGH'))
);

-- Warnings collection table
CREATE TABLE IF NOT EXISTS credit_assessment_warnings (
    id SERIAL PRIMARY KEY,
    assessment_id UUID NOT NULL REFERENCES credit_assessments(assessment_id) ON DELETE CASCADE,
    warning VARCHAR(500) NOT NULL
);

-- Strengths collection table
CREATE TABLE IF NOT EXISTS credit_assessment_strengths (
    id SERIAL PRIMARY KEY,
    assessment_id UUID NOT NULL REFERENCES credit_assessments(assessment_id) ON DELETE CASCADE,
    strength VARCHAR(500) NOT NULL
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_merchant_id ON credit_assessments(merchant_id);
CREATE INDEX IF NOT EXISTS idx_assessment_date ON credit_assessments(assessment_date);
CREATE INDEX IF NOT EXISTS idx_credit_score ON credit_assessments(credit_score);
CREATE INDEX IF NOT EXISTS idx_risk_category ON credit_assessments(risk_category);
CREATE INDEX IF NOT EXISTS idx_is_eligible ON credit_assessments(is_eligible);
CREATE INDEX IF NOT EXISTS idx_merchant_date ON credit_assessments(merchant_id, assessment_date DESC);

CREATE INDEX IF NOT EXISTS idx_warnings_assessment ON credit_assessment_warnings(assessment_id);
CREATE INDEX IF NOT EXISTS idx_strengths_assessment ON credit_assessment_strengths(assessment_id);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger for auto-updating updated_at
DROP TRIGGER IF EXISTS update_credit_assessments_updated_at ON credit_assessments;
CREATE TRIGGER update_credit_assessments_updated_at
    BEFORE UPDATE ON credit_assessments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- View for latest assessments per merchant
CREATE OR REPLACE VIEW v_latest_assessments AS
SELECT DISTINCT ON (merchant_id) *
FROM credit_assessments
ORDER BY merchant_id, assessment_date DESC;

-- View for assessment statistics
CREATE OR REPLACE VIEW v_assessment_stats AS
SELECT 
    risk_category,
    COUNT(*) as count,
    AVG(credit_score) as avg_credit_score,
    AVG(eligible_loan_amount) as avg_loan_amount,
    SUM(CASE WHEN is_eligible THEN 1 ELSE 0 END) as eligible_count
FROM credit_assessments
WHERE assessment_date > CURRENT_DATE - INTERVAL '30 days'
GROUP BY risk_category;

-- Sample queries
-- =============================================

-- Get latest assessment for a merchant
-- SELECT * FROM v_latest_assessments WHERE merchant_id = 'MERCHANT_001';

-- Get all eligible merchants
-- SELECT * FROM credit_assessments WHERE is_eligible = TRUE ORDER BY credit_score DESC;

-- Get merchants needing re-assessment (no assessment in last 30 days)
-- SELECT DISTINCT merchant_id FROM credit_assessments 
-- WHERE merchant_id NOT IN (
--     SELECT merchant_id FROM credit_assessments 
--     WHERE assessment_date > CURRENT_DATE - INTERVAL '30 days'
-- );

-- Get assessment statistics by risk category
-- SELECT * FROM v_assessment_stats;

-- Clean up old assessments (older than 2 years)
-- DELETE FROM credit_assessments WHERE assessment_date < CURRENT_DATE - INTERVAL '2 years';

-- =============================================
-- End of Schema
-- =============================================
