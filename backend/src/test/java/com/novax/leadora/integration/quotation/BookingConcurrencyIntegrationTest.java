package com.novax.leadora.integration.quotation;

import com.novax.leadora.api.dto.request.AcceptQuotationRequest;
import com.novax.leadora.application.usecase.quotation.AcceptQuotationUseCase;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.*;
import com.novax.leadora.infrastructure.persistence.entity.enums.*;
import com.novax.leadora.infrastructure.persistence.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.ai.google.genai.vertex-ai=false",
        "spring.ai.google.genai.api-key=dummy-api-key",
        "spring.ai.google.genai.embedding.api-key=dummy-api-key"
})
@ActiveProfiles("dev")
class BookingConcurrencyIntegrationTest {

    @Autowired
    private AcceptQuotationUseCase acceptQuotationUseCase;

    @Autowired
    private ProductServiceRepository productServiceRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private QuotationRepository quotationRepository;

    @Autowired
    private QuotationDetailRepository quotationDetailRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingDetailRepository bookingDetailRepository;

    @Autowired
    private QuotationAcceptanceLogRepository acceptanceLogRepository;

    private ProductServiceEntity roomProduct;
    private CustomerEntity customer;
    private DealEntity deal;
    private QuotationEntity quotationA;
    private QuotationEntity quotationB;

    @BeforeEach
    void setUp() {
        // Cleanup first
        cleanup();

        // 1. Save Room Product with totalRooms = 1
        roomProduct = ProductServiceEntity.builder()
                .name("Concurrency Room Type")
                .category(ProductCategory.ROOM)
                .status(ProductStatus.ACTIVE)
                .unitPrice(BigDecimal.valueOf(100))
                .unit("Room")
                .totalRooms(1)
                .build();
        roomProduct = productServiceRepository.save(roomProduct);

        // 2. Save Customer
        customer = CustomerEntity.builder()
                .fullName("Concurrency Customer")
                .customerType(CustomerType.INDIVIDUAL)
                .status(CustomerStatus.ACTIVE)
                .build();
        customer = customerRepository.save(customer);

        // 3. Save Deal
        deal = DealEntity.builder()
                .dealName("Concurrency Deal")
                .customer(customer)
                .pipelineStage(DealPipelineStage.QUOTATION_SENT)
                .status(DealStatus.OPEN)
                .build();
        deal = dealRepository.save(deal);

        // 4. Save Quotation A (valid, token active)
        quotationA = QuotationEntity.builder()
                .deal(deal)
                .customer(customer)
                .version(1)
                .roomType("Concurrency Room Type")
                .checkInDate(LocalDate.now())
                .checkOutDate(LocalDate.now().plusDays(1))
                .status(QuotationStatus.SENT)
                .subtotal(BigDecimal.valueOf(100))
                .discountPercent(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(100))
                .acceptanceToken("concurrency-token-A")
                .tokenExpiry(OffsetDateTime.now().plusDays(1))
                .tokenUsed(false)
                .build();
        quotationA = quotationRepository.save(quotationA);

        QuotationDetailEntity detailA = QuotationDetailEntity.builder()
                .quotation(quotationA)
                .productService(roomProduct)
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(100))
                .nights(1)
                .lineTotal(BigDecimal.valueOf(100))
                .build();
        quotationDetailRepository.save(detailA);

        // 5. Save Quotation B (valid, token active)
        quotationB = QuotationEntity.builder()
                .deal(deal)
                .customer(customer)
                .version(1)
                .roomType("Concurrency Room Type")
                .checkInDate(LocalDate.now())
                .checkOutDate(LocalDate.now().plusDays(1))
                .status(QuotationStatus.SENT)
                .subtotal(BigDecimal.valueOf(100))
                .discountPercent(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(100))
                .acceptanceToken("concurrency-token-B")
                .tokenExpiry(OffsetDateTime.now().plusDays(1))
                .tokenUsed(false)
                .build();
        quotationB = quotationRepository.save(quotationB);

        QuotationDetailEntity detailB = QuotationDetailEntity.builder()
                .quotation(quotationB)
                .productService(roomProduct)
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(100))
                .nights(1)
                .lineTotal(BigDecimal.valueOf(100))
                .build();
        quotationDetailRepository.save(detailB);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        if (quotationA != null && quotationA.getQuotationId() != null) {
            deleteBookingsForQuotation(quotationA.getQuotationId());
            deleteAcceptanceLogsForQuotation(quotationA.getQuotationId());
            quotationDetailRepository
                    .deleteAll(quotationDetailRepository.findByQuotation_QuotationId(quotationA.getQuotationId()));
            quotationRepository.deleteById(quotationA.getQuotationId());
        }
        if (quotationB != null && quotationB.getQuotationId() != null) {
            deleteBookingsForQuotation(quotationB.getQuotationId());
            deleteAcceptanceLogsForQuotation(quotationB.getQuotationId());
            quotationDetailRepository
                    .deleteAll(quotationDetailRepository.findByQuotation_QuotationId(quotationB.getQuotationId()));
            quotationRepository.deleteById(quotationB.getQuotationId());
        }
        if (deal != null && deal.getDealId() != null) {
            dealRepository.deleteById(deal.getDealId());
        }
        if (customer != null && customer.getCustomerId() != null) {
            customerRepository.deleteById(customer.getCustomerId());
        }
        if (roomProduct != null && roomProduct.getProductId() != null) {
            productServiceRepository.deleteById(roomProduct.getProductId());
        }
    }

    private void deleteBookingsForQuotation(UUID quotationId) {
        List<BookingEntity> bookings = bookingRepository.findAll().stream()
                .filter(b -> b.getQuotation() != null && quotationId.equals(b.getQuotation().getQuotationId()))
                .toList();
        for (BookingEntity b : bookings) {
            bookingDetailRepository.deleteAll(bookingDetailRepository.findAll().stream()
                    .filter(bd -> bd.getBooking() != null && b.getBookingId().equals(bd.getBooking().getBookingId()))
                    .toList());
            bookingRepository.deleteById(b.getBookingId());
        }
    }

    private void deleteAcceptanceLogsForQuotation(UUID quotationId) {
        List<QuotationAcceptanceLogEntity> logs = acceptanceLogRepository.findAll().stream()
                .filter(l -> l.getQuotation() != null && quotationId.equals(l.getQuotation().getQuotationId()))
                .toList();
        acceptanceLogRepository.deleteAll(logs);
    }

    @Test
    @DisplayName("IT-CONCUR-01: Concurrently accepting quotation for only 1 available room -> one wins, one gets ROOM_UNAVAILABLE")
    void testConcurrentQuotationAcceptance() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicReference<BusinessException> businessExceptionRef = new AtomicReference<>();
        List<String> logs = new CopyOnWriteArrayList<>();

        AcceptQuotationRequest requestA = new AcceptQuotationRequest();
        requestA.setNotes("First request notes.");

        AcceptQuotationRequest requestB = new AcceptQuotationRequest();
        requestB.setNotes("Second request notes.");

        executorService.submit(() -> {
            try {
                startLatch.await();
                acceptQuotationUseCase.execute("concurrency-token-A", requestA, "127.0.0.1", "Thread-A-Agent");
                successCount.incrementAndGet();
                logs.add("Thread-A succeeded");
            } catch (BusinessException e) {
                failureCount.incrementAndGet();
                businessExceptionRef.set(e);
                logs.add("Thread-A failed: " + e.getErrorCode());
            } catch (Exception e) {
                failureCount.incrementAndGet();
                logs.add("Thread-A errored: " + e.getMessage());
            } finally {
                finishLatch.countDown();
            }
        });

        executorService.submit(() -> {
            try {
                startLatch.await();
                acceptQuotationUseCase.execute("concurrency-token-B", requestB, "127.0.0.1", "Thread-B-Agent");
                successCount.incrementAndGet();
                logs.add("Thread-B succeeded");
            } catch (BusinessException e) {
                failureCount.incrementAndGet();
                businessExceptionRef.set(e);
                logs.add("Thread-B failed: " + e.getErrorCode());
            } catch (Exception e) {
                failureCount.incrementAndGet();
                logs.add("Thread-B errored: " + e.getMessage());
            } finally {
                finishLatch.countDown();
            }
        });

        // Trigger simultaneous start
        startLatch.countDown();

        // Wait for threads to finish
        assertTrue(finishLatch.await(5, TimeUnit.SECONDS), "Concurrent requests did not complete in time");
        executorService.shutdown();

        // Output execution logs for debug context
        System.out.println("Execution Logs: " + logs);

        // Assert exactly 1 success and 1 failure
        assertEquals(1, successCount.get(), "Expected exactly 1 success");
        assertEquals(1, failureCount.get(), "Expected exactly 1 failure");

        // Verify the failure reason is ROOM_UNAVAILABLE
        assertNotNull(businessExceptionRef.get(), "Expected a BusinessException");
        assertEquals("ROOM_UNAVAILABLE", businessExceptionRef.get().getErrorCode(),
                "Failure code must be ROOM_UNAVAILABLE");

        // Verify database state: 1 booking created for our test quotations
        List<BookingEntity> bookings = bookingRepository.findAll().stream()
                .filter(b -> b.getQuotation() != null &&
                        (quotationA.getQuotationId().equals(b.getQuotation().getQuotationId()) ||
                                quotationB.getQuotationId().equals(b.getQuotation().getQuotationId())))
                .toList();
        assertEquals(1, bookings.size(), "Expected exactly 1 booking in database for test quotations");

        QuotationEntity updatedA = quotationRepository.findById(quotationA.getQuotationId()).orElseThrow();
        QuotationEntity updatedB = quotationRepository.findById(quotationB.getQuotationId()).orElseThrow();

        if (updatedA.getStatus() == QuotationStatus.CONVERTED) {
            assertTrue(updatedA.getTokenUsed());
            assertEquals(QuotationStatus.SENT, updatedB.getStatus());
            assertFalse(updatedB.getTokenUsed());
        } else {
            assertEquals(QuotationStatus.CONVERTED, updatedB.getStatus());
            assertTrue(updatedB.getTokenUsed());
            assertEquals(QuotationStatus.SENT, updatedA.getStatus());
            assertFalse(updatedA.getTokenUsed());
        }
    }
}
