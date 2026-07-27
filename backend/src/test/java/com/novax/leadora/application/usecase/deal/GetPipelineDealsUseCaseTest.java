package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.api.dto.response.PipelineDealCardResponse;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetPipelineDealsUseCaseTest {

    @Mock
    private DealRepository dealRepository;

    @Mock
    private QuotationRepository quotationRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Spy
    private DealMapper dealMapper = new DealMapper();

    @Mock
    private DealAccessPolicy dealAccessPolicy;

    @InjectMocks
    private GetPipelineDealsUseCase getPipelineDealsUseCase;

    @Test
    @SuppressWarnings("unchecked")
    void execute_returnsDealsWithResolvedWorkflowIndicators() {
        // Arrange
        UUID dealId = UUID.randomUUID();
        DealEntity deal = new DealEntity();
        deal.setDealId(dealId);
        deal.setDealName("Test Deal");

        UserEntity user = new UserEntity();
        user.setUserId(UUID.randomUUID());

        when(dealAccessPolicy.currentUser()).thenReturn(user);
        when(dealAccessPolicy.listScopeOwnerId(user)).thenReturn(null);
        when(dealRepository.findAll(any(Specification.class))).thenReturn(List.of(deal));

        QuotationEntity activeQuotation = new QuotationEntity();
        activeQuotation.setQuotationId(UUID.randomUUID());
        activeQuotation.setStatus(QuotationStatus.SENT);
        activeQuotation.setDeal(deal);
        activeQuotation.setCreatedAt(OffsetDateTime.now());

        when(quotationRepository.findByDeal_DealIdIn(List.of(dealId))).thenReturn(List.of(activeQuotation));

        BookingEntity activeBooking = new BookingEntity();
        activeBooking.setBookingId(UUID.randomUUID());
        activeBooking.setStatus(BookingStatus.CONFIRMED);
        activeBooking.setQuotation(activeQuotation);
        activeBooking.setCreatedAt(OffsetDateTime.now());

        when(bookingRepository.findByQuotation_QuotationIdIn(List.of(activeQuotation.getQuotationId())))
                .thenReturn(List.of(activeBooking));

        PaymentEntity payment = new PaymentEntity();
        payment.setBooking(activeBooking);
        payment.setStatus(PaymentStatus.PAID);
        payment.setCreatedAt(OffsetDateTime.now());

        when(paymentRepository.findByBooking_BookingIdIn(List.of(activeBooking.getBookingId())))
                .thenReturn(List.of(payment));

        // Act
        List<PipelineDealCardResponse> result = getPipelineDealsUseCase.execute(null, null);

        // Assert
        assertThat(result).hasSize(1);
        PipelineDealCardResponse card = result.get(0);
        assertThat(card.getDeal().getId()).isEqualTo(dealId);
        assertThat(card.isHasActiveQuotation()).isTrue();
        assertThat(card.getActiveQuotationStatus()).isEqualTo("SENT");
        assertThat(card.isHasActiveBooking()).isTrue();
        assertThat(card.getActiveBookingStatus()).isEqualTo("CONFIRMED");
        assertThat(card.getPaymentStatus()).isEqualTo("PAID");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_noDeals_returnsEmptyList() {
        // Arrange
        UserEntity user = new UserEntity();
        when(dealAccessPolicy.currentUser()).thenReturn(user);
        when(dealAccessPolicy.listScopeOwnerId(user)).thenReturn(null);
        when(dealRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());

        // Act
        List<PipelineDealCardResponse> result = getPipelineDealsUseCase.execute(null, null);

        // Assert
        assertThat(result).isEmpty();
        verifyNoInteractions(quotationRepository, bookingRepository, paymentRepository);
    }
}
