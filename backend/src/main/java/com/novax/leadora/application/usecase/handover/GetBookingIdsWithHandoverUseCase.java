package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.infrastructure.persistence.repository.OpHandoverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * UC-20.1 — which confirmed bookings already have an operational handover.
 *
 * <p>Exists so the "bookings still waiting for a handover" list can be computed on the server. The
 * frontend used to page through the whole handover list and build the set itself, which was wrong
 * three ways at once: it asked for more rows than the API allows, it read the page metadata from a
 * field the API does not send (so it only ever saw the first page), and once the handover list
 * became owner-scoped the set silently excluded colleagues' handovers.
 */
@Service
@RequiredArgsConstructor
public class GetBookingIdsWithHandoverUseCase {

    private final OpHandoverRepository opHandoverRepository;

    @Transactional(readOnly = true)
    public List<UUID> execute() {
        return opHandoverRepository.findBookingIdsWithHandover();
    }
}
