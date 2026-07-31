package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealStageHistoryEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.repository.DealStageHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Appends a row to {@code deal_stage_history} whenever a deal changes pipeline stage.
 *
 * <p>Called explicitly from the four places that move a deal, rather than hooked onto a JPA entity
 * listener. A listener would have to reconstruct the previous stage from the persistence context and
 * could not tell a manual move from a background sync — and every caller here already has both
 * facts to hand, because it computes them for the activity log anyway.
 *
 * <p>{@code Propagation.MANDATORY} is the point of the whole class: the write joins the caller's
 * transaction, so the history row and the stage change commit or roll back together. If this ran in
 * its own transaction the table would drift out of step with {@code deals} exactly the way
 * {@code activity_log} can, and metrics computed from it would be quietly wrong instead of loudly
 * broken.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordDealStageChangeService {

    /** Where a transition came from — kept as constants so the values stay greppable. */
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_CREATED = "CREATED";
    public static final String SOURCE_WORKFLOW_SYNC = "WORKFLOW_SYNC";
    public static final String SOURCE_AUTO_WIN = "AUTO_WIN";

    private final DealStageHistoryRepository dealStageHistoryRepository;
    private final CurrentUserProvider currentUserProvider;

    /**
     * @param deal      the deal being moved, already carrying its new stage
     * @param fromStage the stage being left; null when the deal is entering the pipeline
     * @param toStage   the stage being entered
     * @param source    one of the {@code SOURCE_*} constants
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(DealEntity deal, DealPipelineStage fromStage, DealPipelineStage toStage,
                       String source) {
        if (deal == null || deal.getDealId() == null || toStage == null || fromStage == toStage) {
            return;
        }
        dealStageHistoryRepository.save(DealStageHistoryEntity.builder()
                .dealId(deal.getDealId())
                .fromStage(fromStage)
                .toStage(toStage)
                .changedAt(OffsetDateTime.now())
                .changedBy(resolveActorId())
                .source(source)
                .backfilled(false)
                .build());
    }

    /**
     * Null for a move made by a scheduler or a gateway callback — there is no authenticated user in
     * those flows, and inventing one would misattribute the change.
     */
    private UUID resolveActorId() {
        try {
            UserEntity actor = currentUserProvider.resolveQuietly();
            return actor == null ? null : actor.getUserId();
        } catch (RuntimeException ex) {
            log.debug("No actor resolvable for stage-history row: {}", ex.getMessage());
            return null;
        }
    }
}
