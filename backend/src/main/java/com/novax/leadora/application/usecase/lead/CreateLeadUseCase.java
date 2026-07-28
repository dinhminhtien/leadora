package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.api.dto.request.CreateLeadRequest;
import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import static com.novax.leadora.common.util.TextUtils.blankToNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateLeadUseCase {

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final StartSlaTrackingUseCase startSlaTrackingUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final NotificationRepository notificationRepository;
    private final LeadContactPolicy leadContactPolicy;

    @Transactional
    public LeadResponse execute(CreateLeadRequest request) {
        // BR: an organization lead must name its company (also enforced client-side).
        if (Boolean.TRUE.equals(request.getIsCorporate()) && !StringUtils.hasText(request.getCompanyName())) {
            throw new IllegalArgumentException("Company name is required for an organization lead.");
        }

        // A lead must be contactable, and must not duplicate a lead or a customer. Both rules are
        // shared with UpdateLeadUseCase — see LeadContactPolicy for why they are not written out
        // here. Reachability is checked first: "you left both contact fields empty" is a more
        // useful answer than a duplicate report on whichever one field was filled in.
        leadContactPolicy.assertReachable(request.getEmail(), request.getPhone());
        leadContactPolicy.assertNoDuplicate(request.getEmail(), request.getPhone(), null);

        UserEntity assignedUser = null;
        if (request.getAssignedUserId() != null) {
            // Same contract as UpdateLeadUseCase: an unknown assignee is a 404, not a
            // silent fall-back to an unassigned draft.
            assignedUser = userRepository.findById(request.getAssignedUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.getAssignedUserId()));
        }

        // Who typed it in. Non-fatal if unresolved.
        //
        // Creating a lead does NOT make you its owner. A sales rep who records a walk-in used to be
        // auto-assigned it, which quietly handed out work that is a Manager's to distribute: the
        // rep's own queue grew by whatever they happened to type in, the lead started an SLA clock
        // nobody had accepted, and the distribution step the process is built around never
        // happened. `created_by` alone still lets them find and correct their own entry
        // (LeadAccessPolicy.owns treats creator and assignee alike for read/edit), which is the
        // access they actually need — everything that requires ownership (status changes,
        // conversion) stays blocked until a Manager assigns it.
        UserEntity creator = null;
        try {
            creator = currentUserProvider.resolve(null);
        } catch (Exception e) {
            log.warn("Could not resolve creator for new lead: {}", e.getMessage());
        }

        // Every optional column is normalised — a blank form field is "not provided", not the
        // value "". See TextUtils.blankToNull: `leads` carries partial unique indexes on
        // lower(email) and phone WHERE the column IS NOT NULL, and "" is not null, so the first
        // lead saved without an email claimed that slot and every later one collided with it. The
        // user saw "a lead with this email already exists" for a field they had left empty.
        LeadEntity lead = LeadEntity.builder()
                .fullName(request.getFullName().trim())
                .email(blankToNull(request.getEmail()))
                .phone(blankToNull(request.getPhone()))
                .companyName(blankToNull(request.getCompanyName()))
                .address(blankToNull(request.getAddress()))
                .isCorporate(Boolean.TRUE.equals(request.getIsCorporate()))
                .source(blankToNull(request.getSource()))
                .interestedService(blankToNull(request.getInterestedService()))
                .status(LeadStatus.NEW)
                .notes(blankToNull(request.getNotes()))
                .assignedUser(assignedUser)
                .createdBy(creator)
                .build();

        LeadEntity saved = leadRepository.save(lead);

        // BR-06: SLA/follow-up enforcement begins only once a lead is assigned to a
        // sales rep. An unassigned draft starts no SLA timer (hence no warning/breach/
        // escalation, no overdue flag, no reminder). When the lead is later assigned,
        // UpdateLeadUseCase starts tracking then.
        if (assignedUser != null) {
            try {
                startSlaTrackingUseCase.execute("LEAD_RESPONSE", "LEAD", saved.getLeadId());
            } catch (Exception e) {
                log.warn("SLA tracking failed for lead {}: {}", saved.getLeadId(), e.getMessage());
            }
        }

        // UC-15.1: notify the assigned sales rep, if the lead was created already assigned
        // by someone else. Skip self-assignment (a rep creating their own lead) — no point
        // telling them they were "assigned" a lead they just created.
        if (assignedUser != null && !assignedUser.equals(creator)) {
            notifyLeadAssigned(saved, assignedUser);
        }

        return LeadResponse.from(saved);
    }


    private void notifyLeadAssigned(LeadEntity lead, UserEntity assignedUser) {
        try {
            NotificationEntity notification = NotificationEntity.builder()
                    .user(assignedUser)
                    .title("New Lead Assigned")
                    .message("You were assigned a new lead: " + lead.getFullName())
                    .type("LEAD_ASSIGNED")
                    .relatedEntity("LEAD")
                    .relatedId(lead.getLeadId())
                    .build();
            notificationRepository.save(notification);
        } catch (Exception e) {
            log.warn("Lead-assigned notification failed for lead {}: {}", lead.getLeadId(), e.getMessage());
        }
    }
}
