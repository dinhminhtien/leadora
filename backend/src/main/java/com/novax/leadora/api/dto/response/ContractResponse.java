package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BillingMethod;
import com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import com.novax.leadora.infrastructure.persistence.entity.enums.PdfStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractResponse {

    private UUID id;
    private String contractCode;
    private UUID dealId;
    private String dealName;
    private UUID quotationId;
    private UUID customerId;
    private String customerName;
    private UUID contactId;
    private String contactName;
    private CustomerType customerTypeSnapshot;
    private BillingMethod billingMethod;
    private int version;
    private ContractStatus status;
    private UUID parentContractId;
    private JsonNode commercialSnapshot;
    private BigDecimal totalContractValue;
    private LocalDate validUntil;
    private String pdfUrl;
    private PdfStatus pdfStatus;
    private OffsetDateTime sentAt;
    private String sentTo;
    private OffsetDateTime acknowledgedAt;
    private OffsetDateTime effectiveDate;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static ContractResponse fromEntity(ContractEntity entity) {
        if (entity == null)
            return null;

        JsonNode snapshotNode = null;
        try {
            if (entity.getCommercialSnapshot() != null && !entity.getCommercialSnapshot().isBlank()) {
                snapshotNode = objectMapper.readTree(entity.getCommercialSnapshot());
                if (snapshotNode.isObject()) {
                    com.fasterxml.jackson.databind.node.ObjectNode obj = (com.fasterxml.jackson.databind.node.ObjectNode) snapshotNode;
                    if (!obj.has("checkInDate") && entity.getQuotation() != null
                            && entity.getQuotation().getCheckInDate() != null) {
                        obj.put("checkInDate", entity.getQuotation().getCheckInDate().toString());
                    }
                    if (!obj.has("checkOutDate") && entity.getQuotation() != null
                            && entity.getQuotation().getCheckOutDate() != null) {
                        obj.put("checkOutDate", entity.getQuotation().getCheckOutDate().toString());
                    }
                    if (!obj.has("totalAmount")) {
                        obj.put("totalAmount",
                                entity.getTotalContractValue() != null ? entity.getTotalContractValue().doubleValue()
                                        : 0.0);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return ContractResponse.builder()
                .id(entity.getId())
                .contractCode(entity.getContractCode())
                .dealId(entity.getDeal() != null ? entity.getDeal().getDealId() : null)
                .dealName(entity.getDeal() != null ? entity.getDeal().getDealName() : null)
                .quotationId(entity.getQuotation() != null ? entity.getQuotation().getQuotationId() : null)
                .customerId(entity.getCustomer() != null ? entity.getCustomer().getCustomerId() : null)
                .customerName(entity.getCustomer() != null ? entity.getCustomer().getFullName() : null)
                .contactId(entity.getContact() != null ? entity.getContact().getContactId() : null)
                .contactName(entity.getContact() != null ? entity.getContact().getFullName() : null)
                .customerTypeSnapshot(entity.getCustomerTypeSnapshot())
                .billingMethod(entity.getBillingMethod())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .parentContractId(entity.getParentContractId())
                .commercialSnapshot(snapshotNode)
                .totalContractValue(entity.getTotalContractValue())
                .validUntil(entity.getValidUntil())
                .pdfUrl(entity.getPdfUrl())
                .pdfStatus(entity.getPdfStatus())
                .sentAt(entity.getSentAt())
                .sentTo(entity.getSentTo())
                .acknowledgedAt(entity.getAcknowledgedAt())
                .effectiveDate(entity.getEffectiveDate())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
