package com.lumexpay.vortexa.credit.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.lumexpay.vortexa.credit.model.CreditAssessment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Service for generating PDF credit assessment reports.
 */
@Service
@Slf4j
public class PdfReportService {

    @Value("${storage.reports.path:/tmp/credit-reports}")
    private String reportsPath;

    @Value("${storage.reports.url-prefix:http://localhost:8080/api/v1/reports}")
    private String reportsUrlPrefix;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // Colors
    private static final Color PRIMARY_COLOR = new Color(25, 65, 115);       // Dark Blue
    private static final Color SECONDARY_COLOR = new Color(70, 130, 180);    // Steel Blue
    private static final Color SUCCESS_COLOR = new Color(40, 167, 69);       // Green
    private static final Color WARNING_COLOR = new Color(255, 193, 7);       // Yellow
    private static final Color DANGER_COLOR = new Color(220, 53, 69);        // Red
    private static final Color LIGHT_GRAY = new Color(245, 245, 245);

    /**
     * Generate PDF credit report.
     *
     * @param assessment Credit assessment data
     * @return Report URL
     */
    public String generateReport(CreditAssessment assessment) throws IOException {
        log.info("Generating PDF report for merchant: {}", assessment.getMerchantId());

        // Ensure reports directory exists
        Path reportsDir = Paths.get(reportsPath);
        if (!Files.exists(reportsDir)) {
            Files.createDirectories(reportsDir);
        }

        // Generate filename
        String fileName = String.format("credit_report_%s_%s.pdf",
            assessment.getMerchantId(),
            LocalDateTime.now().format(FILE_DATE_FORMAT));
        String filePath = reportsPath + File.separator + fileName;

        // Create document
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(filePath));
            writer.setPageEvent(new HeaderFooterPageEvent(assessment.getMerchantId()));
            
            document.open();

            // Add content
            addHeader(document, assessment);
            addCreditScoreSummary(document, assessment);
            addEligibilitySection(document, assessment);
            addFinancialMetrics(document, assessment);
            addPerformanceMetrics(document, assessment);
            addScoreBreakdown(document, assessment);
            addWarningsAndStrengths(document, assessment);
            addRecommendations(document, assessment);
            addDisclaimer(document);

            document.close();

            log.info("PDF report generated successfully: {}", filePath);

            // Update assessment with report details
            String reportUrl = reportsUrlPrefix + "/" + fileName;
            assessment.setReportUrl(reportUrl);
            assessment.setReportFileName(fileName);

            return reportUrl;

        } catch (DocumentException e) {
            throw new IOException("Failed to generate PDF report", e);
        }
    }

    /**
     * Add report header.
     */
    private void addHeader(Document document, CreditAssessment assessment) throws DocumentException {
        // Title
        Font titleFont = new Font(Font.HELVETICA, 24, Font.BOLD, PRIMARY_COLOR);
        Paragraph title = new Paragraph("CREDIT ASSESSMENT REPORT", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        // Subtitle
        Font subtitleFont = new Font(Font.HELVETICA, 12, Font.NORMAL, Color.GRAY);
        Paragraph subtitle = new Paragraph("Merchant Lending Marketplace", subtitleFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(20);
        document.add(subtitle);

        // Merchant info table
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setSpacingAfter(20);

        addInfoRow(infoTable, "Merchant ID:", assessment.getMerchantId());
        addInfoRow(infoTable, "Assessment Date:", assessment.getAssessmentDate().format(DATE_FORMAT));
        addInfoRow(infoTable, "Report ID:", assessment.getAssessmentId().toString().substring(0, 8).toUpperCase());

        document.add(infoTable);

        // Separator
        LineSeparator separator = new LineSeparator(1, 100, PRIMARY_COLOR, Element.ALIGN_CENTER, -5);
        document.add(separator);
        document.add(Chunk.NEWLINE);
    }

    /**
     * Add credit score summary section.
     */
    private void addCreditScoreSummary(Document document, CreditAssessment assessment) throws DocumentException {
        // Section title
        addSectionTitle(document, "Credit Score Summary");

        // Score display
        PdfPTable scoreTable = new PdfPTable(3);
        scoreTable.setWidthPercentage(100);
        scoreTable.setWidths(new float[]{1, 1, 1});
        scoreTable.setSpacingAfter(20);

        // Credit Score
        PdfPCell scoreCell = createScoreCell(
            assessment.getCreditScore().toString(),
            "CREDIT SCORE",
            getScoreColor(assessment.getCreditScore())
        );
        scoreTable.addCell(scoreCell);

        // Risk Category
        Color riskColor = switch (assessment.getRiskCategory()) {
            case LOW -> SUCCESS_COLOR;
            case MEDIUM -> WARNING_COLOR;
            case HIGH -> DANGER_COLOR;
        };
        PdfPCell riskCell = createScoreCell(
            assessment.getRiskCategory().name(),
            "RISK CATEGORY",
            riskColor
        );
        scoreTable.addCell(riskCell);

        // Eligibility
        Color eligColor = assessment.getIsEligible() ? SUCCESS_COLOR : DANGER_COLOR;
        PdfPCell eligCell = createScoreCell(
            assessment.getIsEligible() ? "ELIGIBLE" : "NOT ELIGIBLE",
            "LOAN STATUS",
            eligColor
        );
        scoreTable.addCell(eligCell);

        document.add(scoreTable);
    }

    /**
     * Add eligibility section.
     */
    private void addEligibilitySection(Document document, CreditAssessment assessment) throws DocumentException {
        addSectionTitle(document, "Loan Eligibility");

        if (assessment.getIsEligible()) {
            PdfPTable eligTable = new PdfPTable(2);
            eligTable.setWidthPercentage(100);
            eligTable.setSpacingAfter(20);

            addEligibilityRow(eligTable, "Eligible Loan Amount", 
                "₹" + formatAmount(assessment.getEligibleLoanAmount()));
            addEligibilityRow(eligTable, "Maximum Tenure",
                assessment.getMaxTenureDays() + " days (" + (assessment.getMaxTenureDays() / 30) + " months)");
            addEligibilityRow(eligTable, "Recommended Interest Rate",
                assessment.getRecommendedInterestRate() + "% p.a.");

            // EMI estimate (rough calculation)
            BigDecimal monthlyRate = assessment.getRecommendedInterestRate()
                .divide(new BigDecimal("1200"), 6, RoundingMode.HALF_UP);
            int tenureMonths = assessment.getMaxTenureDays() / 30;
            if (tenureMonths > 0) {
                BigDecimal emi = calculateEMI(assessment.getEligibleLoanAmount(), monthlyRate, tenureMonths);
                addEligibilityRow(eligTable, "Estimated Monthly EMI", "₹" + formatAmount(emi));
            }

            document.add(eligTable);
        } else {
            Font reasonFont = new Font(Font.HELVETICA, 11, Font.ITALIC, DANGER_COLOR);
            Paragraph reason = new Paragraph("Reason: " + assessment.getIneligibilityReason(), reasonFont);
            reason.setSpacingAfter(20);
            document.add(reason);
        }
    }

    /**
     * Add financial metrics section.
     */
    private void addFinancialMetrics(Document document, CreditAssessment assessment) throws DocumentException {
        addSectionTitle(document, "Financial Metrics");

        PdfPTable metricsTable = new PdfPTable(2);
        metricsTable.setWidthPercentage(100);
        metricsTable.setSpacingAfter(20);

        addMetricRow(metricsTable, "Last 3 Months Volume", "₹" + formatAmount(assessment.getLast3MonthsVolume()));
        addMetricRow(metricsTable, "Last 6 Months Volume", "₹" + formatAmount(assessment.getLast6MonthsVolume()));
        addMetricRow(metricsTable, "Last 12 Months Volume", "₹" + formatAmount(assessment.getLast12MonthsVolume()));
        addMetricRow(metricsTable, "Average Monthly Volume", "₹" + formatAmount(assessment.getAverageMonthlyVolume()));
        addMetricRow(metricsTable, "Average Transaction Value", "₹" + formatAmount(assessment.getAverageTransactionValue()));
        addMetricRow(metricsTable, "Total Transactions", String.valueOf(assessment.getTransactionCount()));
        addMetricRow(metricsTable, "Unique Customers", String.valueOf(assessment.getUniqueCustomerCount()));

        document.add(metricsTable);
    }

    /**
     * Add performance metrics section.
     */
    private void addPerformanceMetrics(Document document, CreditAssessment assessment) throws DocumentException {
        addSectionTitle(document, "Performance Metrics");

        PdfPTable perfTable = new PdfPTable(2);
        perfTable.setWidthPercentage(100);
        perfTable.setSpacingAfter(20);

        addMetricRowWithIndicator(perfTable, "Consistency Score", 
            assessment.getConsistencyScore().setScale(1, RoundingMode.HALF_UP) + "/100",
            assessment.getConsistencyScore().compareTo(new BigDecimal("70")) >= 0);
        
        addMetricRowWithIndicator(perfTable, "Growth Rate",
            assessment.getGrowthRate().setScale(1, RoundingMode.HALF_UP) + "%",
            assessment.getGrowthRate().compareTo(BigDecimal.ZERO) >= 0);
        
        addMetricRowWithIndicator(perfTable, "Bounce Rate",
            assessment.getBounceRate().setScale(1, RoundingMode.HALF_UP) + "%",
            assessment.getBounceRate().compareTo(new BigDecimal("10")) <= 0);
        
        addMetricRowWithIndicator(perfTable, "Customer Concentration",
            assessment.getCustomerConcentration().setScale(1, RoundingMode.HALF_UP) + "%",
            assessment.getCustomerConcentration().compareTo(new BigDecimal("50")) <= 0);

        document.add(perfTable);
    }

    /**
     * Add score breakdown section.
     */
    private void addScoreBreakdown(Document document, CreditAssessment assessment) throws DocumentException {
        addSectionTitle(document, "Score Breakdown");

        PdfPTable breakdownTable = new PdfPTable(4);
        breakdownTable.setWidthPercentage(100);
        breakdownTable.setWidths(new float[]{2, 1, 1, 1});
        breakdownTable.setSpacingAfter(20);

        // Header row
        Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
        addBreakdownHeader(breakdownTable, "Component", headerFont);
        addBreakdownHeader(breakdownTable, "Score", headerFont);
        addBreakdownHeader(breakdownTable, "Weight", headerFont);
        addBreakdownHeader(breakdownTable, "Contribution", headerFont);

        // Data rows
        addBreakdownRow(breakdownTable, "Transaction Volume", 
            assessment.getVolumeScore(), new BigDecimal("0.30"));
        addBreakdownRow(breakdownTable, "Business Consistency",
            assessment.getConsistencyScore(), new BigDecimal("0.25"));
        addBreakdownRow(breakdownTable, "Growth Trend",
            assessment.getGrowthScore(), new BigDecimal("0.15"));
        addBreakdownRow(breakdownTable, "Payment Success Rate",
            assessment.getBounceRateScore(), new BigDecimal("0.15"));
        addBreakdownRow(breakdownTable, "Customer Diversification",
            assessment.getConcentrationScore(), new BigDecimal("0.15"));

        // Total row
        Font totalFont = new Font(Font.HELVETICA, 11, Font.BOLD, PRIMARY_COLOR);
        PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL CREDIT SCORE", totalFont));
        totalLabel.setColspan(3);
        totalLabel.setPadding(8);
        totalLabel.setBackgroundColor(LIGHT_GRAY);
        breakdownTable.addCell(totalLabel);

        PdfPCell totalScore = new PdfPCell(new Phrase(assessment.getCreditScore().toString(), totalFont));
        totalScore.setPadding(8);
        totalScore.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalScore.setBackgroundColor(LIGHT_GRAY);
        breakdownTable.addCell(totalScore);

        document.add(breakdownTable);
    }

    /**
     * Add warnings and strengths section.
     */
    private void addWarningsAndStrengths(Document document, CreditAssessment assessment) throws DocumentException {
        // Strengths
        if (assessment.getStrengths() != null && !assessment.getStrengths().isEmpty()) {
            addSectionTitle(document, "Strengths");
            
            Font strengthFont = new Font(Font.HELVETICA, 10, Font.NORMAL, SUCCESS_COLOR);
            for (String strength : assessment.getStrengths()) {
                Paragraph p = new Paragraph("✓ " + strength, strengthFont);
                p.setSpacingAfter(5);
                document.add(p);
            }
            document.add(Chunk.NEWLINE);
        }

        // Warnings
        if (assessment.getWarnings() != null && !assessment.getWarnings().isEmpty()) {
            addSectionTitle(document, "Risk Factors");

            Font warningFont = new Font(Font.HELVETICA, 10, Font.NORMAL, WARNING_COLOR);
            for (String warning : assessment.getWarnings()) {
                Paragraph p = new Paragraph("⚠ " + warning, warningFont);
                p.setSpacingAfter(5);
                document.add(p);
            }
            document.add(Chunk.NEWLINE);
        }
    }

    /**
     * Add recommendations section.
     */
    private void addRecommendations(Document document, CreditAssessment assessment) throws DocumentException {
        addSectionTitle(document, "Recommendations");

        Font recFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.DARK_GRAY);

        if (assessment.getIsEligible()) {
            switch (assessment.getRiskCategory()) {
                case LOW -> {
                    document.add(new Paragraph("• Excellent credit profile - suitable for premium loan products", recFont));
                    document.add(new Paragraph("• Consider offering increased credit limits after 3 months", recFont));
                    document.add(new Paragraph("• Eligible for longer tenure products", recFont));
                }
                case MEDIUM -> {
                    document.add(new Paragraph("• Good credit profile - standard loan products recommended", recFont));
                    document.add(new Paragraph("• Monitor bounce rate and volume consistency", recFont));
                    document.add(new Paragraph("• Re-assess after 30 days for improved terms", recFont));
                }
                case HIGH -> {
                    document.add(new Paragraph("• Higher risk profile - short-term loans recommended", recFont));
                    document.add(new Paragraph("• Require daily/weekly repayment structure", recFont));
                    document.add(new Paragraph("• Consider collateral or guarantor requirements", recFont));
                }
            }
        } else {
            document.add(new Paragraph("• Build transaction history for at least 3 months", recFont));
            document.add(new Paragraph("• Reduce failed transaction rate below 20%", recFont));
            document.add(new Paragraph("• Increase average monthly volume above ₹25,000", recFont));
        }

        document.add(Chunk.NEWLINE);
    }

    /**
     * Add disclaimer section.
     */
    private void addDisclaimer(Document document) throws DocumentException {
        LineSeparator separator = new LineSeparator(0.5f, 100, Color.LIGHT_GRAY, Element.ALIGN_CENTER, -5);
        document.add(separator);

        Font disclaimerFont = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);
        Paragraph disclaimer = new Paragraph(
            "DISCLAIMER: This credit assessment is based on UPI transaction data and is provided for " +
            "informational purposes only. Final lending decisions should consider additional factors " +
            "including KYC verification, external credit bureau data, and lender-specific policies. " +
            "This report does not constitute a guarantee of loan approval. " +
            "Generated by Sentient Credit Assessment Engine v1.0.",
            disclaimerFont
        );
        disclaimer.setAlignment(Element.ALIGN_JUSTIFIED);
        disclaimer.setSpacingBefore(20);
        document.add(disclaimer);
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private void addSectionTitle(Document document, String title) throws DocumentException {
        Font sectionFont = new Font(Font.HELVETICA, 14, Font.BOLD, PRIMARY_COLOR);
        Paragraph section = new Paragraph(title, sectionFont);
        section.setSpacingBefore(15);
        section.setSpacingAfter(10);
        document.add(section);
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.GRAY);
        Font valueFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);

        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(5);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(5);
        table.addCell(valueCell);
    }

    private PdfPCell createScoreCell(String value, String label, Color color) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(color);
        cell.setBorderWidth(2);
        cell.setPadding(15);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setMinimumHeight(80);

        Font valueFont = new Font(Font.HELVETICA, 24, Font.BOLD, color);
        Font labelFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.GRAY);

        Paragraph content = new Paragraph();
        content.add(new Chunk(value + "\n", valueFont));
        content.add(new Chunk(label, labelFont));
        content.setAlignment(Element.ALIGN_CENTER);

        cell.addElement(content);
        return cell;
    }

    private void addEligibilityRow(PdfPTable table, String label, String value) {
        Font labelFont = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.DARK_GRAY);
        Font valueFont = new Font(Font.HELVETICA, 11, Font.BOLD, PRIMARY_COLOR);

        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(8);
        labelCell.setBackgroundColor(LIGHT_GRAY);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(8);
        valueCell.setBackgroundColor(LIGHT_GRAY);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private void addMetricRow(PdfPTable table, String label, String value) {
        Font labelFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.DARK_GRAY);
        Font valueFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);

        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.BOTTOM);
        labelCell.setBorderColor(Color.LIGHT_GRAY);
        labelCell.setPadding(8);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.BOTTOM);
        valueCell.setBorderColor(Color.LIGHT_GRAY);
        valueCell.setPadding(8);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private void addMetricRowWithIndicator(PdfPTable table, String label, String value, boolean isPositive) {
        Font labelFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.DARK_GRAY);
        Font valueFont = new Font(Font.HELVETICA, 10, Font.BOLD, isPositive ? SUCCESS_COLOR : WARNING_COLOR);

        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.BOTTOM);
        labelCell.setBorderColor(Color.LIGHT_GRAY);
        labelCell.setPadding(8);
        table.addCell(labelCell);

        String indicator = isPositive ? "✓ " : "⚠ ";
        PdfPCell valueCell = new PdfPCell(new Phrase(indicator + value, valueFont));
        valueCell.setBorder(Rectangle.BOTTOM);
        valueCell.setBorderColor(Color.LIGHT_GRAY);
        valueCell.setPadding(8);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private void addBreakdownHeader(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(PRIMARY_COLOR);
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addBreakdownRow(PdfPTable table, String component, BigDecimal score, BigDecimal weight) {
        Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
        BigDecimal contribution = score.multiply(weight).setScale(1, RoundingMode.HALF_UP);

        PdfPCell compCell = new PdfPCell(new Phrase(component, normalFont));
        compCell.setPadding(6);
        table.addCell(compCell);

        PdfPCell scoreCell = new PdfPCell(new Phrase(score.setScale(0, RoundingMode.HALF_UP).toString(), normalFont));
        scoreCell.setPadding(6);
        scoreCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(scoreCell);

        PdfPCell weightCell = new PdfPCell(new Phrase(weight.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP) + "%", normalFont));
        weightCell.setPadding(6);
        weightCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(weightCell);

        PdfPCell contribCell = new PdfPCell(new Phrase(contribution.toString(), normalFont));
        contribCell.setPadding(6);
        contribCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(contribCell);
    }

    private Color getScoreColor(int score) {
        if (score >= 80) return SUCCESS_COLOR;
        if (score >= 60) return WARNING_COLOR;
        return DANGER_COLOR;
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0";
        
        BigDecimal lakhs = amount.divide(new BigDecimal("100000"), 2, RoundingMode.HALF_UP);
        if (lakhs.compareTo(BigDecimal.ONE) >= 0) {
            return lakhs.setScale(2, RoundingMode.HALF_UP) + " Lakhs";
        }
        
        BigDecimal thousands = amount.divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
        if (thousands.compareTo(BigDecimal.ONE) >= 0) {
            return thousands.setScale(2, RoundingMode.HALF_UP) + "K";
        }
        
        return amount.setScale(0, RoundingMode.HALF_UP).toString();
    }

    private BigDecimal calculateEMI(BigDecimal principal, BigDecimal monthlyRate, int tenureMonths) {
        // EMI = P * r * (1+r)^n / ((1+r)^n - 1)
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal onePlusRPowN = onePlusR.pow(tenureMonths);
        
        BigDecimal numerator = principal.multiply(monthlyRate).multiply(onePlusRPowN);
        BigDecimal denominator = onePlusRPowN.subtract(BigDecimal.ONE);
        
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(new BigDecimal(tenureMonths), 0, RoundingMode.HALF_UP);
        }
        
        return numerator.divide(denominator, 0, RoundingMode.HALF_UP);
    }

    /**
     * Page event handler for headers and footers.
     */
    private static class HeaderFooterPageEvent extends PdfPageEventHelper {
        private final String merchantId;

        public HeaderFooterPageEvent(String merchantId) {
            this.merchantId = merchantId;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            
            // Footer
            Font footerFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
            Phrase footer = new Phrase(
                "Sentient Credit Assessment Engine | Merchant: " + merchantId + " | Page " + writer.getPageNumber(),
                footerFont
            );
            ColumnText.showTextAligned(
                cb, Element.ALIGN_CENTER, footer,
                (document.right() - document.left()) / 2 + document.leftMargin(),
                document.bottom() - 20, 0
            );
        }
    }

    /**
     * Get report file path for download.
     */
    public String getReportFilePath(String fileName) {
        return reportsPath + File.separator + fileName;
    }
}
