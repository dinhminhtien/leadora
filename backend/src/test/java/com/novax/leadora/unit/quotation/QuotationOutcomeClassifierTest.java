package com.novax.leadora.unit.quotation;

import com.novax.leadora.application.usecase.quotation.QuotationOutcome;
import com.novax.leadora.application.usecase.quotation.QuotationOutcomeClassifier;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The one place "what became of this quotation" is decided, so the rules are pinned here. */
class QuotationOutcomeClassifierTest {

    private static final boolean SENT = true;
    private static final boolean UNSENT = false;
    private static final boolean REPLACED = true;
    private static final boolean CURRENT = false;

    @Test
    @DisplayName("accepted and converted are wins")
    void winsAreAcceptedAndConverted() {
        assertThat(QuotationOutcomeClassifier.classify(QuotationStatus.ACCEPTED, SENT, CURRENT))
                .isEqualTo(QuotationOutcome.WON);
        assertThat(QuotationOutcomeClassifier.classify(QuotationStatus.CONVERTED, SENT, CURRENT))
                .isEqualTo(QuotationOutcome.WON);
    }

    @ParameterizedTest
    @EnumSource(value = QuotationStatus.class, names = { "REJECTED", "EXPIRED", "CLOSED" })
    @DisplayName("a terminal quotation is a loss only if a customer ever saw it")
    void dispatchDecidesLossOrAbandonment(QuotationStatus status) {
        assertThat(QuotationOutcomeClassifier.classify(status, SENT, CURRENT))
                .isEqualTo(QuotationOutcome.LOST);
        assertThat(QuotationOutcomeClassifier.classify(status, UNSENT, CURRENT))
                .as("rejected at approval or expired as a draft — nobody outside saw it")
                .isEqualTo(QuotationOutcome.ABANDONED);
    }

    @ParameterizedTest
    @EnumSource(QuotationStatus.class)
    @DisplayName("a replaced revision is superseded whatever its status says")
    void replacementBeatsEveryStatus(QuotationStatus status) {
        assertThat(QuotationOutcomeClassifier.classify(status, SENT, REPLACED))
                .as("the status is overwritten by whatever touched the row next; the child is not")
                .isEqualTo(QuotationOutcome.SUPERSEDED);
    }

    @Test
    @DisplayName("the SUPERSEDED status is honoured even with no child row")
    void supersededStatusAloneIsEnough() {
        assertThat(QuotationOutcomeClassifier.classify(QuotationStatus.SUPERSEDED, SENT, CURRENT))
                .as("otherwise a superseded row whose child was deleted reads as still awaiting an answer")
                .isEqualTo(QuotationOutcome.SUPERSEDED);
    }

    @ParameterizedTest
    @EnumSource(value = QuotationStatus.class,
            names = { "DRAFT", "PENDING_APPROVAL", "APPROVED", "SENT", "PENDING_REVISION", "INTERESTED" })
    @DisplayName("anything not terminal is still in flight")
    void inFlightStatusesAreOpen(QuotationStatus status) {
        assertThat(QuotationOutcomeClassifier.classify(status, SENT, CURRENT))
                .isEqualTo(QuotationOutcome.OPEN);
    }

    @Test
    @DisplayName("only wins and losses settle the win-rate denominator")
    void decidedIsWinsAndLosses() {
        assertThat(QuotationOutcome.WON.isDecided()).isTrue();
        assertThat(QuotationOutcome.LOST.isDecided()).isTrue();
        assertThat(QuotationOutcome.OPEN.isDecided()).isFalse();
        assertThat(QuotationOutcome.ABANDONED.isDecided()).isFalse();
        assertThat(QuotationOutcome.SUPERSEDED.isDecided()).isFalse();
    }

    @Test
    @DisplayName("every outcome but a replaced revision is a live opportunity")
    void liveExcludesOnlySuperseded() {
        for (QuotationOutcome outcome : QuotationOutcome.values()) {
            assertThat(outcome.isLive()).isEqualTo(outcome != QuotationOutcome.SUPERSEDED);
        }
    }

    @ParameterizedTest
    @EnumSource(QuotationStatus.class)
    @DisplayName("a bucket round-trips through the encoding the queries use")
    void bucketRoundTrips(QuotationStatus status) {
        for (boolean sent : new boolean[] { true, false }) {
            for (boolean replaced : new boolean[] { true, false }) {
                String bucket = QuotationOutcomeClassifier.bucket(status, sent, replaced);
                assertThat(QuotationOutcomeClassifier.statusOf(bucket)).isEqualTo(status);
                assertThat(QuotationOutcomeClassifier.isSent(bucket)).isEqualTo(sent);
                assertThat(QuotationOutcomeClassifier.isReplaced(bucket)).isEqualTo(replaced);
                assertThat(QuotationOutcomeClassifier.classifyBucket(bucket))
                        .isEqualTo(QuotationOutcomeClassifier.classify(status, sent, replaced));
            }
        }
    }

    @Test
    @DisplayName("an unknown status leaves one figure unclassified rather than failing the report")
    void unknownStatusReadsAsOpen() {
        assertThat(QuotationOutcomeClassifier.classifyBucket("SOMETHING_NEW|SENT|CURRENT"))
                .isEqualTo(QuotationOutcome.OPEN);
        assertThat(QuotationOutcomeClassifier.classifyBucket(null)).isEqualTo(QuotationOutcome.OPEN);
        assertThat(QuotationOutcomeClassifier.classifyBucket("")).isEqualTo(QuotationOutcome.OPEN);
    }

    @Test
    @DisplayName("a replaced row is superseded even when the status is not recognised")
    void replacementSurvivesAnUnknownStatus() {
        assertThat(QuotationOutcomeClassifier.classifyBucket("SOMETHING_NEW|SENT|REPLACED"))
                .isEqualTo(QuotationOutcome.SUPERSEDED);
    }
}
