package com.novax.leadora.application.usecase.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractPdfGenerator {

    private final ObjectMapper objectMapper;

    public byte[] generate(ContractEntity contract) {
        log.info("Generating PDF for contract: {}", contract.getContractCode());
        
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            // Font configurations
            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(30, 58, 95));
            Font subtitleFont = new Font(Font.HELVETICA, 12, Font.ITALIC, Color.GRAY);
            Font sectionHeaderFont = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(30, 58, 95));
            Font boldFont = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);

            // Title
            Paragraph title = new Paragraph("COMMERCIAL SALES CONTRACT", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(4);
            document.add(title);

            Paragraph codeSub = new Paragraph("Contract Code: " + contract.getContractCode(), subtitleFont);
            codeSub.setAlignment(Element.ALIGN_CENTER);
            codeSub.setSpacingAfter(20);
            document.add(codeSub);

            // Parse commercial snapshot JSON
            JsonNode snapshot = objectMapper.readTree(contract.getCommercialSnapshot());
            
            // Section 1: Customer Details
            document.add(new Paragraph("1. PARTIES", sectionHeaderFont));
            document.add(new Paragraph(" ", normalFont));
            
            PdfPTable partiesTable = new PdfPTable(2);
            partiesTable.setWidthPercentage(100);
            partiesTable.setSpacingAfter(15);
            
            partiesTable.addCell(createCell("Customer Name:", boldFont, Color.WHITE, false));
            partiesTable.addCell(createCell(snapshot.path("customer").path("name").asText("N/A"), normalFont, Color.WHITE, false));
            
            partiesTable.addCell(createCell("Customer Type:", boldFont, Color.WHITE, false));
            partiesTable.addCell(createCell(contract.getCustomerTypeSnapshot().name(), normalFont, Color.WHITE, false));

            if (snapshot.path("customer").has("companyName") && !snapshot.path("customer").path("companyName").asText().isBlank()) {
                partiesTable.addCell(createCell("Company Name:", boldFont, Color.WHITE, false));
                partiesTable.addCell(createCell(snapshot.path("customer").path("companyName").asText(), normalFont, Color.WHITE, false));
            }
            
            partiesTable.addCell(createCell("Billing Method:", boldFont, Color.WHITE, false));
            partiesTable.addCell(createCell(contract.getBillingMethod().name(), normalFont, Color.WHITE, false));
            
            document.add(partiesTable);

            // Section 2: Room Block Details
            document.add(new Paragraph("2. ROOM BLOCK SUMMARY", sectionHeaderFont));
            document.add(new Paragraph(" ", normalFont));

            PdfPTable roomsTable = new PdfPTable(5);
            roomsTable.setWidthPercentage(100);
            roomsTable.setSpacingAfter(15);
            
            // Header Row
            Color headerBg = new Color(30, 58, 95);
            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
            roomsTable.addCell(createCell("Room Type", headerFont, headerBg, true));
            roomsTable.addCell(createCell("Quantity", headerFont, headerBg, true));
            roomsTable.addCell(createCell("Nights", headerFont, headerBg, true));
            roomsTable.addCell(createCell("Rate per Night", headerFont, headerBg, true));
            roomsTable.addCell(createCell("Total", headerFont, headerBg, true));

            JsonNode rooms = snapshot.path("rooms");
            if (rooms.isArray()) {
                for (JsonNode room : rooms) {
                    roomsTable.addCell(createCell(room.path("roomType").asText("—"), normalFont, Color.WHITE, false));
                    roomsTable.addCell(createCell(String.valueOf(room.path("quantity").asInt(0)), normalFont, Color.WHITE, false));
                    roomsTable.addCell(createCell(String.valueOf(room.path("nights").asInt(0)), normalFont, Color.WHITE, false));
                    roomsTable.addCell(createCell(formatCurrency(new BigDecimal(room.path("pricePerNight").doubleValue())), normalFont, Color.WHITE, false));
                    roomsTable.addCell(createCell(formatCurrency(new BigDecimal(room.path("lineTotal").doubleValue())), normalFont, Color.WHITE, false));
                }
            } else {
                // Fallback from QuotationEntity live properties if rooms array is not populated
                roomsTable.addCell(createCell(contract.getQuotation().getRoomType() != null ? contract.getQuotation().getRoomType() : "—", normalFont, Color.WHITE, false));
                roomsTable.addCell(createCell("—", normalFont, Color.WHITE, false));
                roomsTable.addCell(createCell("—", normalFont, Color.WHITE, false));
                roomsTable.addCell(createCell("—", normalFont, Color.WHITE, false));
                roomsTable.addCell(createCell(formatCurrency(contract.getQuotation().getTotalAmount()), normalFont, Color.WHITE, false));
            }
            document.add(roomsTable);

            // Pricing summary block
            PdfPTable pricingTable = new PdfPTable(2);
            pricingTable.setWidthPercentage(40);
            pricingTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            pricingTable.setSpacingAfter(20);

            JsonNode pricing = snapshot.path("pricing");
            pricingTable.addCell(createCell("Subtotal:", boldFont, Color.WHITE, false));
            pricingTable.addCell(createCell(formatCurrency(new BigDecimal(pricing.path("subtotal").doubleValue())), normalFont, Color.WHITE, false));

            double discountAmount = pricing.path("discountAmount").doubleValue();
            if (discountAmount > 0) {
                pricingTable.addCell(createCell("Discount:", boldFont, Color.WHITE, false));
                pricingTable.addCell(createCell("-" + formatCurrency(new BigDecimal(discountAmount)), normalFont, Color.WHITE, false));
            }

            pricingTable.addCell(createCell("Total Contract Value:", boldFont, Color.LIGHT_GRAY, false));
            pricingTable.addCell(createCell(formatCurrency(contract.getTotalContractValue()), boldFont, Color.LIGHT_GRAY, false));
            document.add(pricingTable);

            // Section 3: Commercial Terms
            document.add(new Paragraph("3. TERMS & POLICIES", sectionHeaderFont));
            document.add(new Paragraph(" ", normalFont));

            PdfPTable termsTable = new PdfPTable(2);
            termsTable.setWidthPercentage(100);
            termsTable.setSpacingAfter(25);

            termsTable.addCell(createCell("Payment Terms:", boldFont, Color.WHITE, false));
            termsTable.addCell(createCell(snapshot.path("paymentTerms").asText("As per hotel standard policy."), normalFont, Color.WHITE, false));

            termsTable.addCell(createCell("Cancellation Policy:", boldFont, Color.WHITE, false));
            termsTable.addCell(createCell(snapshot.path("cancellationPolicy").asText("As per hotel standard policy."), normalFont, Color.WHITE, false));

            termsTable.addCell(createCell("Contract Expiry:", boldFont, Color.WHITE, false));
            termsTable.addCell(createCell("Valid until " + contract.getValidUntil().toString() + ". Must be accepted before this date.", normalFont, Color.WHITE, false));

            document.add(termsTable);

            // Signatures
            document.add(new Paragraph("4. SIGNATURES", sectionHeaderFont));
            document.add(new Paragraph(" ", normalFont));

            PdfPTable sigTable = new PdfPTable(2);
            sigTable.setWidthPercentage(100);
            sigTable.setSpacingBefore(15);

            PdfPCell leftCell = new PdfPCell(new Paragraph("For the Hotel (Leadora)\n\n\n\n_______________________\nAuthorized Signature", normalFont));
            leftCell.setBorder(PdfPCell.NO_BORDER);
            sigTable.addCell(leftCell);

            PdfPCell rightCell = new PdfPCell(new Paragraph("For the Customer\n\n\n\n_______________________\nCustomer Signature", normalFont));
            rightCell.setBorder(PdfPCell.NO_BORDER);
            sigTable.addCell(rightCell);

            document.add(sigTable);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generating contract PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF document", e);
        }
    }

    private PdfPCell createCell(String text, Font font, Color background, boolean isHeader) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(background);
        cell.setPadding(6);
        if (isHeader) {
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        }
        return cell;
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "—";
        return NumberFormat.getNumberInstance(Locale.of("vi", "VN")).format(amount) + " ₫";
    }
}
