package com.novax.leadora.integration.lead;

import com.novax.leadora.api.dto.request.CreateLeadRequest;
import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.application.usecase.lead.CreateLeadUseCase;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadIntegrationTest {

    @Mock
    private LeadRepository leadRepository;

    @InjectMocks
    private CreateLeadUseCase createLeadUseCase;

    @Test
    @DisplayName("IT-LEAD-01: Create lead flow persists lead entity and returns initial NEW status")
    void testCreateLeadIntegrationFlow() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setFullName("Tran Van Integration");
        request.setEmail("tranvan@leadora.vn");
        request.setPhone("0987654321");
        request.setCompanyName("Novax Corp");
        request.setIsCorporate(true);

        LeadEntity mockSavedLead = LeadEntity.builder()
                .leadId(UUID.randomUUID())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .companyName(request.getCompanyName())
                .isCorporate(true)
                .status(LeadStatus.NEW)
                .build();

        when(leadRepository.save(any(LeadEntity.class))).thenReturn(mockSavedLead);

        LeadResponse response = createLeadUseCase.execute(request);

        assertNotNull(response);
        assertEquals(LeadStatus.NEW, response.getStatus());
        assertEquals("Tran Van Integration", response.getFullName());
        verify(leadRepository, times(1)).save(any(LeadEntity.class));
    }
}
