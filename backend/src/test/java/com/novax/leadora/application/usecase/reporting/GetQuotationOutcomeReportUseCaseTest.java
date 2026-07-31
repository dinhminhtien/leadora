package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.QuotationOutcomeReportResponse;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** UC-23.5 — the outcome rates that a status snapshot alone cannot produce. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetQuotationOutcomeReportUseCaseTest {

    @Mock
    private QuotationRepository quotationRepository;

    private GetQuotationOutcomeReportUseCase useCase;
    private List<Object[]> statusRows;

    @BeforeEach
    void setUp() {
        statusRows = new ArrayList<>();
        useCase = new GetQuotationOutcomeReportUseCase(
                quotationRepository, new ReportRangeFactory("Asia/Ho_Chi_Minh"));
        when(quotationRepository.aggregateByStatus(any(), any())).thenReturn(statusRows);
        when(quotationRepository.countApproved(any(), any(), any())).thenReturn(0L);
        when(quotationRepository.countRejectedByApprover(any(), any(), any())).thenReturn(0L);
    }

    private void status(QuotationStatus status, long count) {
        statusRows.add(new Object[] { status, count });
    }

    private QuotationOutcomeReportResponse run() {
        return useCase.execute(LocalDate.now().minusDays(30), LocalDate.now());
    }

    @Test
    @DisplayName("superseded revisions leave the denominator but are still reported")
    void supersededRevisionsDoNotDiluteTheRates() {
        // One quotation negotiated through three revisions, the final version accepted.
        status(QuotationStatus.SUPERSEDED, 3);
        status(QuotationStatus.ACCEPTED, 1);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getTotal()).as("only the live version counts").isEqualTo(1);
        assertThat(report.getSuperseded()).isEqualTo(3);
        assertThat(report.getAcceptanceRate())
                .as("acceptance must not fall just because the deal took several rounds")
                .isEqualTo(100.0);
    }

    @Test
    @DisplayName("approval rate comes from approved_at, not from rows parked at status APPROVED")
    void approvalRateSurvivesTheStatusBeingOverwritten() {
        // Everything approved was promptly sent on, so nothing is left at APPROVED.
        status(QuotationStatus.SENT, 8);
        status(QuotationStatus.REJECTED, 2);
        when(quotationRepository.countApproved(any(), any(), any())).thenReturn(8L);
        when(quotationRepository.countRejectedByApprover(any(), any(), any())).thenReturn(2L);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getApproved()).isEqualTo(8);
        assertThat(report.getRejectedByApprover()).isEqualTo(2);
        assertThat(report.getApprovalRate()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("a customer rejection is not counted against the approver")
    void customerRejectionIsSeparateFromApproverRejection() {
        status(QuotationStatus.REJECTED, 5);
        when(quotationRepository.countApproved(any(), any(), any())).thenReturn(5L);
        // All five were approved and then turned down by the customer, so none is an approver reject.
        when(quotationRepository.countRejectedByApprover(any(), any(), any())).thenReturn(0L);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getRejected()).as("the raw status count is still shown").isEqualTo(5);
        assertThat(report.getRejectedByApprover()).isZero();
        assertThat(report.getApprovalRate()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("a converted quotation counts as accepted")
    void convertedCountsTowardsAcceptance() {
        status(QuotationStatus.ACCEPTED, 1);
        status(QuotationStatus.CONVERTED, 3);
        status(QuotationStatus.EXPIRED, 4);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getTotal()).isEqualTo(8);
        assertThat(report.getAcceptanceRate()).isEqualTo(50.0);   // (1 + 3) / 8
        assertThat(report.getConversionRate()).isEqualTo(37.5);   // 3 / 8
    }

    @Test
    @DisplayName("the status breakdown lists only non-empty statuses, in enum order")
    void breakdownSkipsEmptyStatuses() {
        status(QuotationStatus.CONVERTED, 2);
        status(QuotationStatus.DRAFT, 1);

        QuotationOutcomeReportResponse report = run();

        assertThat(report.getByStatus()).hasSize(2);
        assertThat(report.getByStatus().get(0).getStatus()).isEqualTo("DRAFT");
        assertThat(report.getByStatus().get(1).getStatus()).isEqualTo("CONVERTED");
    }

    @Test
    @DisplayName("an empty period renders zeroes rather than dividing by zero")
    void emptyPeriodIsSafe() {
        QuotationOutcomeReportResponse report = run();

        assertThat(report.getTotal()).isZero();
        assertThat(report.getApprovalRate()).isZero();
        assertThat(report.getAcceptanceRate()).isZero();
        assertThat(report.getConversionRate()).isZero();
        assertThat(report.getByStatus()).isEmpty();
    }
}
