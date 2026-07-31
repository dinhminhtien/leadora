package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealStageHistoryEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.repository.DealStageHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecordDealStageChangeServiceTest {

    @Mock
    private DealStageHistoryRepository dealStageHistoryRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private RecordDealStageChangeService service;
    private DealEntity deal;

    @BeforeEach
    void setUp() {
        service = new RecordDealStageChangeService(dealStageHistoryRepository, currentUserProvider);
        deal = new DealEntity();
        deal.setDealId(UUID.randomUUID());
        when(currentUserProvider.resolveQuietly()).thenReturn(null);
    }

    private DealStageHistoryEntity captureSaved() {
        ArgumentCaptor<DealStageHistoryEntity> saved =
                ArgumentCaptor.forClass(DealStageHistoryEntity.class);
        verify(dealStageHistoryRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("records the transition with both stages and a source")
    void recordsATransition() {
        service.record(deal, DealPipelineStage.QUALIFICATION, DealPipelineStage.NEGOTIATION,
                RecordDealStageChangeService.SOURCE_MANUAL);

        DealStageHistoryEntity row = captureSaved();
        assertThat(row.getDealId()).isEqualTo(deal.getDealId());
        assertThat(row.getFromStage()).isEqualTo(DealPipelineStage.QUALIFICATION);
        assertThat(row.getToStage()).isEqualTo(DealPipelineStage.NEGOTIATION);
        assertThat(row.getSource()).isEqualTo("MANUAL");
        assertThat(row.getChangedAt()).isNotNull();
        assertThat(row.isBackfilled()).isFalse();
    }

    @Test
    @DisplayName("a deal entering the pipeline records a null from-stage")
    void entryRowHasNoFromStage() {
        service.record(deal, null, DealPipelineStage.INQUIRY,
                RecordDealStageChangeService.SOURCE_CREATED);

        assertThat(captureSaved().getFromStage())
                .as("without this row, time in the first stage is unmeasurable")
                .isNull();
    }

    @Test
    @DisplayName("attributes the change to the acting user when there is one")
    void attributesToTheActor() {
        UserEntity actor = new UserEntity();
        actor.setUserId(UUID.randomUUID());
        when(currentUserProvider.resolveQuietly()).thenReturn(actor);

        service.record(deal, DealPipelineStage.INQUIRY, DealPipelineStage.QUALIFICATION,
                RecordDealStageChangeService.SOURCE_MANUAL);

        assertThat(captureSaved().getChangedBy()).isEqualTo(actor.getUserId());
    }

    @Test
    @DisplayName("a background move has no actor rather than a made-up one")
    void backgroundMovesHaveNoActor() {
        when(currentUserProvider.resolveQuietly()).thenThrow(new IllegalStateException("no request"));

        service.record(deal, DealPipelineStage.NEGOTIATION, DealPipelineStage.CLOSED_WON,
                RecordDealStageChangeService.SOURCE_AUTO_WIN);

        assertThat(captureSaved().getChangedBy()).isNull();
    }

    @Test
    @DisplayName("a no-op move writes nothing")
    void samStageIsNotRecorded() {
        service.record(deal, DealPipelineStage.NEGOTIATION, DealPipelineStage.NEGOTIATION,
                RecordDealStageChangeService.SOURCE_MANUAL);

        verify(dealStageHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("an unsaved deal or a missing target stage writes nothing")
    void incompleteInputIsIgnored() {
        service.record(null, DealPipelineStage.INQUIRY, DealPipelineStage.NEGOTIATION, "MANUAL");
        service.record(new DealEntity(), DealPipelineStage.INQUIRY, DealPipelineStage.NEGOTIATION, "MANUAL");
        service.record(deal, DealPipelineStage.INQUIRY, null, "MANUAL");

        verify(dealStageHistoryRepository, never()).save(any());
    }
}
