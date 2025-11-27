package com.lumexpay.vortexa.credit.util;

import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class StatisticalCalculator {

    /**
     * Calculate mean of a list of BigDecimal values
     */
    public BigDecimal calculateMean(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(new BigDecimal(values.size()), 4, RoundingMode.HALF_UP);
    }

    /**
     * Calculate standard deviation of a list of BigDecimal values
     */
    public BigDecimal calculateStdDev(List<BigDecimal> values, BigDecimal mean) {
        if (values == null || values.size() < 2) {
            return BigDecimal.ZERO;
        }

        // Convert to double array for Apache Commons Math
        double[] doubleValues = values.stream()
                .mapToDouble(BigDecimal::doubleValue)
                .toArray();

        StandardDeviation stdDev = new StandardDeviation();
        double result = stdDev.evaluate(doubleValues);

        return new BigDecimal(result).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Calculate coefficient of variation (CV = stdDev / mean)
     */
    public BigDecimal calculateCoefficientOfVariation(List<BigDecimal> values) {
        BigDecimal mean = calculateMean(values);
        
        if (mean.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal stdDev = calculateStdDev(values, mean);
        return stdDev.divide(mean, 4, RoundingMode.HALF_UP);
    }

    /**
     * Calculate consistency score (0-100) based on coefficient of variation
     * Lower CV = Higher consistency
     */
    public BigDecimal calculateConsistencyScore(List<BigDecimal> monthlyVolumes) {
        if (monthlyVolumes == null || monthlyVolumes.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal cv = calculateCoefficientOfVariation(monthlyVolumes);
        
        // Consistency score = 100 - (CV * 100)
        // Cap between 0 and 100
        BigDecimal consistencyScore = new BigDecimal(100)
                .subtract(cv.multiply(new BigDecimal(100)));

        return consistencyScore
                .max(BigDecimal.ZERO)
                .min(new BigDecimal(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate growth rate between two periods
     */
    public BigDecimal calculateGrowthRate(BigDecimal currentPeriod, BigDecimal previousPeriod) {
        if (previousPeriod.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal difference = currentPeriod.subtract(previousPeriod);
        return difference.divide(previousPeriod, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate percentage (part / total * 100)
     */
    public BigDecimal calculatePercentage(BigDecimal part, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return part.divide(total, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Cap value between min and max
     */
    public BigDecimal cap(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value.max(min).min(max);
    }

    /**
     * Cap integer value between min and max
     */
    public Integer cap(Integer value, Integer min, Integer max) {
        return Math.max(min, Math.min(max, value));
    }
}