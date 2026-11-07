package com.autoparts.erp.reports.serviceimpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import com.autoparts.erp.reports.dto.DataQualityReportResponse;
import com.autoparts.erp.reports.dto.CustomerAnalyticsResponse;
import com.autoparts.erp.reports.dto.FinancialReconciliationResponse;
import com.autoparts.erp.reports.dto.InventoryAnalyticsResponse;
import com.autoparts.erp.reports.dto.ReportTargetRequest;
import com.autoparts.erp.reports.dto.ScenarioAnalysisRequest;
import com.autoparts.erp.reports.dto.ScenarioAnalysisResponse;
import com.autoparts.erp.reports.dto.TargetActualReportResponse;
import com.autoparts.erp.reports.dto.TargetActualReportResponse.TargetActual;
import com.autoparts.erp.reports.dto.SupplierAnalyticsResponse;
import com.autoparts.erp.reports.repository.AdvancedReportRepository;
import com.autoparts.erp.reports.repository.AdvancedReportRepository.ScenarioBaselineData;
import com.autoparts.erp.reports.service.AdvancedReportService;

@Service
public class AdvancedReportServiceImpl implements AdvancedReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final AdvancedReportRepository repository;
    private final ZoneId businessZone;

    public AdvancedReportServiceImpl(
            AdvancedReportRepository repository,
            @Value("${app.business.time-zone:Asia/Kolkata}")
            String businessTimeZone) {
        this.repository = repository;
        this.businessZone = ZoneId.of(businessTimeZone);
    }

    @Override
    @Transactional(readOnly = true)
    public DataQualityReportResponse getDataQualityReport(
            LocalDate fromDate,
            LocalDate toDate) {
        validateDateRange(fromDate, toDate);
        var fromTimestamp = fromDate.atStartOfDay(businessZone).toOffsetDateTime();
        var toTimestamp = toDate.plusDays(1)
                .atStartOfDay(businessZone).toOffsetDateTime();
        var issues = repository.findDataQualityIssues(
                fromDate, toDate, fromTimestamp, toTimestamp,
                businessZone.getId());
        long recordsChecked = repository.countRecordsChecked(
                fromDate, toDate, fromTimestamp, toTimestamp);
        Set<String> affectedRecords = new HashSet<>();
        issues.forEach(issue ->
                affectedRecords.add(issue.entityType() + ":" + issue.recordId()));
        BigDecimal score = recordsChecked == 0
                ? ONE_HUNDRED
                : BigDecimal.valueOf(recordsChecked - affectedRecords.size())
                        .multiply(ONE_HUNDRED)
                        .divide(BigDecimal.valueOf(recordsChecked), 2, RoundingMode.HALF_UP)
                        .max(ZERO);
        return new DataQualityReportResponse(
                fromDate,
                toDate,
                score,
                recordsChecked,
                affectedRecords.size(),
                OffsetDateTime.now(ZoneOffset.UTC),
                "100 * (records checked - distinct affected records) / records checked; "
                        + "the score is clamped at zero. Checks cover products, "
                        + "customers, suppliers, sales, and purchases.",
                issues);
    }

    @Override
    @Transactional
    public void updateIssueResolution(
            String issueKey,
            String status,
            String notes,
            String username) {
        if (issueKey == null || !issueKey.matches(
                "[A-Z_]+:[A-Z_]+:[0-9]+")) {
            throw new IllegalArgumentException("Invalid issue key");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Authenticated username is required");
        }
        repository.updateIssueResolution(
                issueKey, status, notes, username);
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialReconciliationResponse getReconciliationReport(
            LocalDate fromDate,
            LocalDate toDate) {
        validateDateRange(fromDate, toDate);
        var fromTimestamp = fromDate.atStartOfDay(businessZone).toOffsetDateTime();
        var toTimestamp = toDate.plusDays(1)
                .atStartOfDay(businessZone).toOffsetDateTime();
        return new FinancialReconciliationResponse(
                fromDate,
                toDate,
                OffsetDateTime.now(ZoneOffset.UTC),
                "Discrepancies compare posted transaction totals and stored "
                        + "balances. Supplier credits are reconciled as a single "
                        + "credit pool (applications plus refunds), so neither "
                        + "refunds nor credit applications are counted twice.",
                repository.findReconciliationDiscrepancies(
                        fromDate, toDate, fromTimestamp, toTimestamp,
                        businessZone.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryAnalyticsResponse getInventoryAnalytics(
            LocalDate fromDate,
            LocalDate toDate) {
        validateDateRange(fromDate, toDate);
        return new InventoryAnalyticsResponse(
                fromDate,
                toDate,
                OffsetDateTime.now(ZoneOffset.UTC),
                "ABC: cumulative positive net taxable sales revenue share "
                        + "(A <= 80%, B <= 95%, "
                        + "C remainder). XYZ: population coefficient of variation "
                        + "of monthly net units, normalized to a 30.4375-day month "
                        + "(X <= 0.50, Y <= 1.00, Z > 1.00 or no demand). "
                        + "Movement class uses the last completed sale in the "
                        + "selected period: <=30 days fast, >90 days slow, and no "
                        + "sales in period is explicitly marked. Days since movement "
                        + "is inactivity, not stock age; stock age needs lot-level "
                        + "cost/receipt tracking.",
                "No inventory valuation, GMROI, turnover, or product profitability "
                        + "is returned: the system does not record a reliable "
                        + "inventory-costing/COGS basis.",
                repository.findInventoryMetrics(
                        fromDate,
                        toDate,
                        fromDate.atStartOfDay(businessZone).toOffsetDateTime(),
                        toDate.plusDays(1).atStartOfDay(businessZone)
                                .toOffsetDateTime(),
                        businessZone.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public ScenarioAnalysisResponse simulate(ScenarioAnalysisRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Scenario assumptions are required");
        }
        validateDateRange(request.fromDate(), request.toDate());

        BigDecimal priceChange = defaultZero(request.sellingPriceChangePercent());
        BigDecimal volumeChange = defaultZero(request.salesVolumeChangePercent());
        BigDecimal expenseChange = defaultZero(request.expenseChangePercent());
        ScenarioBaselineData baseline = repository.getScenarioBaseline(
                request.fromDate(), request.toDate());
        BigDecimal revenueBaseline = baseline.revenue();
        BigDecimal revenueScenario = revenueBaseline
                .multiply(ONE_HUNDRED.add(priceChange))
                .multiply(ONE_HUNDRED.add(volumeChange))
                .divide(ONE_HUNDRED.multiply(ONE_HUNDRED), 2, RoundingMode.HALF_UP);
        BigDecimal expenseBaseline = baseline.expenses();
        BigDecimal expenseScenario = expenseBaseline
                .multiply(ONE_HUNDRED.add(expenseChange))
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);

        return new ScenarioAnalysisResponse(
                "HYPOTHETICAL",
                request.fromDate(),
                request.toDate(),
                OffsetDateTime.now(ZoneOffset.UTC),
                revenueBaseline,
                revenueScenario,
                revenueScenario.subtract(revenueBaseline),
                percentageChange(revenueBaseline, revenueScenario),
                expenseBaseline,
                expenseScenario,
                expenseScenario.subtract(expenseBaseline),
                percentageChange(expenseBaseline, expenseScenario),
                null,
                null,
                "Gross profit is not simulated because the current inventory model "
                        + "does not provide reliable transaction-level cost of goods sold.",
                "Revenue scenario = baseline completed-sale taxable revenue * "
                        + "(1 + price change) * (1 + volume change); expense scenario "
                        + "= baseline active expenses * (1 + expense change). "
                        + "Price and volume changes are independent assumptions; "
                        + "the compounding interaction is included. This is a "
                        + "hypothetical simulation only and does not change ERP data.");
    }

    @Override
    @Transactional(readOnly = true)
    public TargetActualReportResponse getTargetActualReport(
            LocalDate fromDate,
            LocalDate toDate) {
        validateDateRange(fromDate, toDate);
        LocalDate today = LocalDate.now(businessZone);
        List<TargetActual> targets = repository
                        .findTargetActuals(fromDate, toDate)
                        .stream()
                        .map(target -> {
                            BigDecimal actual = target.actual() == null
                                    ? ZERO : target.actual();
                            BigDecimal variance = actual.subtract(target.target());
                            BigDecimal achievement = target.target().compareTo(ZERO) == 0
                                    ? null
                                    : actual.multiply(ONE_HUNDRED)
                                            .divide(target.target(), 2, RoundingMode.HALF_UP);
                            BigDecimal remaining = target.target().subtract(actual).max(ZERO);
                            BigDecimal forecast = null;
                            if (!today.isBefore(target.periodStart())) {
                                if (today.isAfter(target.periodEnd())) {
                                    forecast = actual;
                                } else {
                                    long totalDays = ChronoUnit.DAYS.between(
                                            target.periodStart(), target.periodEnd()) + 1L;
                                    long elapsedDays = ChronoUnit.DAYS.between(
                                            target.periodStart(), today) + 1L;
                                    if (totalDays > 0 && elapsedDays > 0) {
                                        forecast = actual
                                                .multiply(BigDecimal.valueOf(totalDays))
                                                .divide(BigDecimal.valueOf(elapsedDays),
                                                        2, RoundingMode.HALF_UP);
                                    }
                                }
                            }
                            return new TargetActual(
                                    target.targetId(),
                                    target.metricKey(),
                                    target.dimensionName(),
                                    target.periodStart(),
                                    target.periodEnd(),
                                    target.target(),
                                    actual,
                                    variance,
                                    achievement,
                                    remaining,
                                    forecast);
                        })
                        .toList();
        return new TargetActualReportResponse(
                        fromDate,
                        toDate,
                        OffsetDateTime.now(ZoneOffset.UTC),
                        "Forecasted achievement is a straight-line projection: actual "
                                + "to date / elapsed calendar days * total target-period "
                                + "calendar days. It is an estimate, not a model prediction. "
                                + "Variance = actual - target; remaining = max(target - actual, 0).",
                        "Gross margin and inventory turnover targets are not enabled because "
                                + "the current schema does not support reliable cost of goods "
                                + "sold and inventory valuation.",
                        targets);
    }

    @Override
    @Transactional
    public UUID createTarget(ReportTargetRequest request, String username) {
        if (request == null) {
            throw new IllegalArgumentException("Target configuration is required");
        }
        validateDateRange(request.periodStart(), request.periodEnd());
        boolean validDimensions = switch (request.metricKey()) {
            case "MONTHLY_SALES", "EXPENSE_BUDGET" ->
                            request.productId() == null && request.categoryId() == null;
            case "PRODUCT_SALES" ->
                            request.productId() != null && request.categoryId() == null;
            case "CATEGORY_REVENUE" ->
                            request.productId() == null && request.categoryId() != null;
            default -> false;
        };
        if (!validDimensions) {
            throw new IllegalArgumentException(
                            "Target dimensions do not match the selected metric");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                            "Authenticated username is required");
        }
        return repository.createTarget(request, username);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerAnalyticsResponse getCustomerAnalytics(
            LocalDate fromDate,
            LocalDate toDate) {
        validateDateRange(fromDate, toDate);
        return new CustomerAnalyticsResponse(
                fromDate,
                toDate,
                OffsetDateTime.now(ZoneOffset.UTC),
                "RFM uses completed sales within the selected period. Recency is "
                        + "days from the period end to last purchase; frequency is "
                        + "distinct invoices; monetary value is taxable sales less "
                        + "accepted returns. Each R/F/M quintile is scored 1-5, with "
                        + "5 representing the strongest value. Cohort month is the "
                        + "customer's first completed-sale month across all history. "
                        + "No customer lifetime value or next-purchase prediction is "
                        + "calculated because margin and validated predictive data "
                        + "are not available.",
                repository.findCustomerMetrics(
                        fromDate,
                        toDate,
                        fromDate.atStartOfDay(businessZone).toOffsetDateTime(),
                        toDate.plusDays(1).atStartOfDay(businessZone)
                                .toOffsetDateTime()));
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierAnalyticsResponse getSupplierAnalytics(
            LocalDate fromDate,
            LocalDate toDate) {
        validateDateRange(fromDate, toDate);
        return new SupplierAnalyticsResponse(
                fromDate,
                toDate,
                OffsetDateTime.now(ZoneOffset.UTC),
                "Lead time is calendar days from purchase_date to received_at; "
                        + "variability is population standard deviation. Payment "
                        + "coverage is purchase payments plus credit applications "
                        + "against those invoices / received invoice total. Days to "
                        + "first payment is measured from purchase date. Return rate "
                        + "is purchase-return amount / "
                        + "received invoice total. Purchase concentration is the "
                        + "supplier's share of received purchases in this period. "
                        + "Delivery timeliness and return defect rates cannot be "
                        + "judged because promised delivery dates and defect reasons "
                        + "are not captured.",
                repository.findSupplierMetrics(
                        fromDate,
                        toDate,
                        toDate.plusDays(1).atStartOfDay(businessZone)
                                .toOffsetDateTime(),
                        businessZone.getId()));
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private static BigDecimal percentageChange(
            BigDecimal baseline,
            BigDecimal simulated) {
        if (baseline.compareTo(ZERO) == 0) {
            return null;
        }
        return simulated.subtract(baseline)
                .multiply(ONE_HUNDRED)
                .divide(baseline.abs(), 2, RoundingMode.HALF_UP);
    }

    private static void validateDateRange(
            LocalDate fromDate,
            LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException(
                    "fromDate and toDate are required");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                    "fromDate must be before or equal to toDate");
        }
    }
}
