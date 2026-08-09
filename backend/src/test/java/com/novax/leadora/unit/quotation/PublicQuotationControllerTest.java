package com.novax.leadora.unit.quotation;

import com.novax.leadora.api.controller.PublicQuotationController;
import com.novax.leadora.api.dto.request.AcceptQuotationRequest;
import com.novax.leadora.api.dto.request.RejectQuotationRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.application.usecase.quotation.AcceptQuotationUseCase;
import com.novax.leadora.application.usecase.quotation.RejectQuotationUseCase;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.infrastructure.persistence.entity.QuotationDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicQuotationControllerTest {

    @Mock
    private QuotationRepository quotationRepository;

    @Mock
    private QuotationDetailRepository quotationDetailRepository;

    @Mock
    private AcceptQuotationUseCase acceptQuotationUseCase;

    @Mock
    private RejectQuotationUseCase rejectQuotationUseCase;

    @InjectMocks
    private PublicQuotationController publicQuotationController;

    @Test
    @DisplayName("UT-PUB-CTRL-01: Get quotation by token successfully")
    void testGetQuotationByTokenSuccess() {
        String token = "secure-token-123";
        UUID quotationId = UUID.randomUUID();
        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.SENT)
                .acceptanceToken(token)
                .build();

        QuotationDetailEntity detail = QuotationDetailEntity.builder()
                .nights(3)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(150))
                .build();

        when(quotationRepository.findByAcceptanceToken(token)).thenReturn(Optional.of(quotation));
        when(quotationDetailRepository.findByQuotation_QuotationId(quotationId)).thenReturn(List.of(detail));

        ResponseEntity<ApiResponse<QuotationResponse>> responseEntity = publicQuotationController
                .getQuotationByToken(token);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody().isSuccess());
        assertNotNull(responseEntity.getBody().getData());
        verify(quotationRepository, times(1)).findByAcceptanceToken(token);
        verify(quotationDetailRepository, times(1)).findByQuotation_QuotationId(quotationId);
    }

    @Test
    @DisplayName("UT-PUB-CTRL-02: Get quotation by token throws ResourceNotFoundException when token not found")
    void testGetQuotationByTokenNotFound() {
        String token = "non-existent-token";
        when(quotationRepository.findByAcceptanceToken(token)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> publicQuotationController.getQuotationByToken(token));
        verify(quotationRepository, times(1)).findByAcceptanceToken(token);
        verifyNoInteractions(quotationDetailRepository);
    }

    @Test
    @DisplayName("UT-PUB-CTRL-03: Accept quotation calls AcceptQuotationUseCase")
    void testAcceptQuotationSuccess() {
        String token = "accept-token";
        AcceptQuotationRequest request = new AcceptQuotationRequest();
        request.setNotes("Please confirm.");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("127.0.0.1");
        servletRequest.addHeader("User-Agent", "Mozilla/5.0");

        ResponseEntity<ApiResponse<Void>> responseEntity = publicQuotationController.acceptQuotation(token, request,
                servletRequest);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        verify(acceptQuotationUseCase, times(1)).execute(eq(token), eq(request), eq("127.0.0.1"), eq("Mozilla/5.0"));
    }

    @Test
    @DisplayName("UT-PUB-CTRL-04: Reject quotation calls RejectQuotationUseCase")
    void testRejectQuotationSuccess() {
        String token = "reject-token";
        RejectQuotationRequest request = new RejectQuotationRequest();
        request.setReason("Price is too high.");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("127.0.0.1");
        servletRequest.addHeader("User-Agent", "Mozilla/5.0");

        ResponseEntity<ApiResponse<Void>> responseEntity = publicQuotationController.rejectQuotation(token, request,
                servletRequest);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        verify(rejectQuotationUseCase, times(1)).execute(eq(token), eq(request), eq("127.0.0.1"), eq("Mozilla/5.0"));
    }
}
