package com.novax.leadora.application.usecase.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.application.event.ContractGeneratedEvent;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.*;
import com.novax.leadora.infrastructure.persistence.entity.enums.*;
import com.novax.leadora.infrastructure.persistence.repository.ContactRepository;
import com.novax.leadora.infrastructure.persistence.repository.ContractRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenerateContractUseCase {

    private final ContractRepository contractRepository;
    private final ContactRepository contactRepository;
    private final QuotationDetailRepository quotationDetailRepository;
    private final com.novax.leadora.infrastructure.persistence.repository.UserRepository userRepository;
    private final ContractCodeGenerator contractCodeGenerator;
    private final ActivityLogPublisher activityLogPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Generates a DRAFT contract for an accepted Quotation.
     * This runs inside the same transactional boundary as the quotation acceptance.
     */
    @Transactional
    public ContractEntity execute(QuotationEntity quotation, UserEntity actor) {
        log.info("Generating contract for quotation: {}", quotation.getQuotationId());

        if (actor != null && actor.getUserId() != null) {
            actor = userRepository.findById(actor.getUserId()).orElse(actor);
        }

        // Check if there is already a contract for this quotation
        List<ContractEntity> existing = contractRepository.findByQuotation_QuotationId(quotation.getQuotationId());
        if (!existing.isEmpty()) {
            log.warn("Quotation {} already has associated contracts. Versioning applies.", quotation.getQuotationId());
        }

        // Get version number
        int nextVersion = existing.size() + 1;

        // Resolve Contact if CORPORATE
        ContactEntity contact = null;
        CustomerEntity customer = quotation.getCustomer();
        if (customer.getCustomerType() == CustomerType.CORPORATE) {
            contact = contactRepository.findByCustomer_CustomerIdAndIsPrimaryTrue(customer.getCustomerId())
                    .orElse(null);
        }

        // Generate contract code
        String contractCode = contractCodeGenerator.generateCode();

        // Snapshot details
        String snapshotJson = buildCommercialSnapshot(quotation, customer, contact);

        // Build contract
        ContractEntity contract = ContractEntity.builder()
                .contractCode(contractCode)
                .deal(quotation.getDeal())
                .quotation(quotation)
                .quotationVersion(quotation.getVersion())
                .customer(customer)
                .contact(contact)
                .customerTypeSnapshot(customer.getCustomerType())
                .billingMethod(BillingMethod.INDIVIDUAL_GUEST_PAYS) // default V1
                .version(nextVersion)
                .status(ContractStatus.DRAFT)
                .commercialSnapshot(snapshotJson)
                .totalContractValue(quotation.getTotalAmount())
                .validUntil(quotation.getValidUntil() != null ? quotation.getValidUntil() : LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(7))
                .pdfStatus(PdfStatus.NONE)
                .createdBy(actor)
                .createdBySource("USER")
                .build();

        if (nextVersion > 1) {
            contract.setParentContractId(existing.get(nextVersion - 2).getId());
        }

        contract = contractRepository.save(contract);
        log.info("Contract generated successfully in DRAFT: {} (id: {})", contract.getContractCode(), contract.getId());

        // Log to central activity log
        activityLogPublisher.publish(
                ActivityLogType.CONTRACT_GENERATED,
                EntityType.CONTRACT,
                contract.getId(),
                "Generated contract " + contract.getContractCode() + " as DRAFT",
                null
        );

        // Publish internal domain event
        eventPublisher.publishEvent(new ContractGeneratedEvent(contract));

        return contract;
    }

    private String buildCommercialSnapshot(QuotationEntity q, CustomerEntity cust, ContactEntity cont) {
        try {
            ObjectNode root = objectMapper.createObjectNode();

            // Quotation snap
            ObjectNode quoteNode = objectMapper.createObjectNode();
            quoteNode.put("id", q.getQuotationId().toString());
            quoteNode.put("version", q.getVersion());
            quoteNode.put("validUntil", q.getValidUntil() != null ? q.getValidUntil().toString() : "");
            root.set("quotation", quoteNode);

            // Customer snap
            ObjectNode custNode = objectMapper.createObjectNode();
            custNode.put("customerId", cust.getCustomerId().toString());
            custNode.put("type", cust.getCustomerType().name());
            custNode.put("name", cust.getFullName());
            custNode.put("companyName", cust.getCompanyName() != null ? cust.getCompanyName() : "");
            custNode.put("email", cust.getEmail() != null ? cust.getEmail() : "");
            custNode.put("phone", cust.getPhone() != null ? cust.getPhone() : "");
            if (cont != null) {
                custNode.put("primaryContactName", cont.getFullName());
                custNode.put("primaryContactEmail", cont.getEmail() != null ? cont.getEmail() : "");
            }
            root.set("customer", custNode);

            // Room blocks / line items snap
            ArrayNode roomsArray = objectMapper.createArrayNode();
            List<QuotationDetailEntity> details = quotationDetailRepository.findByQuotation_QuotationId(q.getQuotationId());
            for (QuotationDetailEntity detail : details) {
                ObjectNode rmNode = objectMapper.createObjectNode();
                String roomType = detail.getProductService() != null ? detail.getProductService().getName() : detail.getDescription();
                rmNode.put("roomType", roomType);
                rmNode.put("quantity", detail.getQuantity());
                rmNode.put("nights", detail.getNights());
                rmNode.put("pricePerNight", detail.getUnitPrice().doubleValue());
                rmNode.put("lineTotal", detail.getLineTotal().doubleValue());
                roomsArray.add(rmNode);
            }
            root.set("rooms", roomsArray);

            // Pricing snap
            ObjectNode priceNode = objectMapper.createObjectNode();
            priceNode.put("subtotal", q.getSubtotal() != null ? q.getSubtotal().doubleValue() : 0.0);
            priceNode.put("discountPercent", q.getDiscountPercent() != null ? q.getDiscountPercent().doubleValue() : 0.0);
            priceNode.put("discountAmount", q.getDiscountAmount() != null ? q.getDiscountAmount().doubleValue() : 0.0);
            priceNode.put("total", q.getTotalAmount() != null ? q.getTotalAmount().doubleValue() : 0.0);
            root.set("pricing", priceNode);

            // Policies & Terms
            root.put("paymentTerms", q.getPaymentPolicy() != null ? q.getPaymentPolicy() : "As per hotel standard policy.");
            root.put("cancellationPolicy", "Free cancellation up to 7 days prior to arrival. Cancellations inside 7 days are subject to penalty.");
            root.put("notes", q.getNotes() != null ? q.getNotes() : "");

            // Root level fields for direct frontend consumption
            root.put("checkInDate", q.getCheckInDate() != null ? q.getCheckInDate().toString() : "");
            root.put("checkOutDate", q.getCheckOutDate() != null ? q.getCheckOutDate().toString() : "");
            root.put("totalAmount", q.getTotalAmount() != null ? q.getTotalAmount().doubleValue() : 0.0);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to build commercial snapshot for contract generation: {}", e.getMessage(), e);
            throw new BusinessException("CONTRACT_SNAPSHOT_FAILED", "Failed to build commercial terms snapshot: " + e.getMessage(), org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
