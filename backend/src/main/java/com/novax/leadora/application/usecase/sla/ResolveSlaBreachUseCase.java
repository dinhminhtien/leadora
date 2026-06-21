package com.novax.leadora.application.usecase.sla;

import com.novax.leadora.infrastructure.persistence.entity.SlaTrackingEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import com.novax.leadora.infrastructure.persistence.repository.SlaTrackingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResolveSlaBreachUseCase {

    private final SlaTrackingRepository slaTrackingRepository;

    /**
     * UC-17.4 step 5-6: User resolves a breached SLA — marks tracking record as RESOLVED.
     *
     * @param trackingId UUID of the SlaTracking record to resolve
     */
    @Transactional
    public void execute(UUID trackingId) {
        SlaTrackingEntity tracking = slaTrackingRepository.findById(trackingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "SLA tracking record not found"));

        // E3: activity already completed
        if (tracking.getStatus() == SlaStatus.RESOLVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Activity already completed");
        }

        // POST-2: update SLA status
        tracking.setStatus(SlaStatus.RESOLVED);
        slaTrackingRepository.save(tracking);

        // POST-3: audit log
        log.info("SLA breach resolved: trackingId={}, entityType={}, entityId={}",
                trackingId, tracking.getEntityType(), tracking.getEntityId());
    }
}