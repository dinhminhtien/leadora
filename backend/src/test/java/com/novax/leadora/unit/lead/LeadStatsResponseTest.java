package com.novax.leadora.unit.lead;

import com.novax.leadora.api.dto.response.LeadStatsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The arithmetic behind the summary tiles.
 *
 * <p>Small surface, but it is the part a reader trusts without checking: a rate that quietly reads
 * 0.0% on an empty database, or an "active" count that double-subtracts, is wrong in a way nobody
 * notices until a decision has been made on it.
 */
class LeadStatsResponseTest {

    @Test
    @DisplayName("active is whatever is neither converted nor lost")
    void derivesActiveFromTheTerminalCounts() {
        LeadStatsResponse stats = LeadStatsResponse.of(32, 12, 5, 7);

        assertThat(stats.getTotal()).isEqualTo(32);
        assertThat(stats.getActive()).isEqualTo(15);
        assertThat(stats.getQualified()).isEqualTo(7);
    }

    @Test
    @DisplayName("rates are a share of ALL leads, so they do not add up to 100%")
    void ratesAreOverTheWholePopulation() {
        LeadStatsResponse stats = LeadStatsResponse.of(32, 12, 5, 7);

        // 12/32 and 5/32 — the remaining 47% is work in progress, which is the honest picture.
        // Dividing by converted+lost instead would report 71% from the same data and make a
        // pipeline that is mostly unfinished look mostly won.
        assertThat(stats.getConvertedRate()).isEqualTo(37.5);
        assertThat(stats.getLostRate()).isEqualTo(15.6);
    }

    @Test
    @DisplayName("rates round to one decimal")
    void roundsToOneDecimal() {
        LeadStatsResponse stats = LeadStatsResponse.of(3, 1, 0, 0);

        assertThat(stats.getConvertedRate()).isEqualTo(33.3);
    }

    @Test
    @DisplayName("an empty set has no rate at all, rather than a rate of zero")
    void reportsNullRatesWhenThereIsNothingToMeasure() {
        LeadStatsResponse stats = LeadStatsResponse.of(0, 0, 0, 0);

        // "0.0%" would claim nothing ever converts; null lets the UI show a dash instead.
        assertThat(stats.getConvertedRate()).isNull();
        assertThat(stats.getLostRate()).isNull();
        assertThat(stats.getActive()).isZero();
    }

    @Test
    @DisplayName("a fully converted set reports 100%")
    void handlesTheAllConvertedCase() {
        LeadStatsResponse stats = LeadStatsResponse.of(4, 4, 0, 0);

        assertThat(stats.getConvertedRate()).isEqualTo(100.0);
        assertThat(stats.getActive()).isZero();
    }
}
