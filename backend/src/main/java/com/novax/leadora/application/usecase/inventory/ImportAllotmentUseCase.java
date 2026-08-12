package com.novax.leadora.application.usecase.inventory;

import com.novax.leadora.api.dto.response.AllotmentImportResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductCategory;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductStatus;
import com.novax.leadora.infrastructure.persistence.repository.ProductServiceRepository;
import com.novax.leadora.infrastructure.persistence.repository.RoomAllotmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Bulk-loads allotment from the spreadsheet the hotel sends.
 *
 * <p>Publishing a month of quota a range at a time is tolerable; a month of quota that varies by
 * day and room type is not, and that is the normal case. The hotel already produces the figures
 * in a file, so the file is the natural input.
 *
 * <p><b>Rows are matched by room name, and that is a deliberate exception.</b> Everywhere else in
 * this feature the room is identified by {@code product_id}, because names drift and matching on
 * them silently attaches records to the wrong room. But the hotel's spreadsheet has never heard
 * of our product ids — a name is all it carries. The safeguard is that an unmatched or ambiguous
 * name is <em>rejected and reported</em>, never guessed at: a row that lands on the wrong room
 * type would misstate what can be sold, which is worse than a row that does not land at all.
 *
 * <p>CSV only. Reading real spreadsheets would pull in a parsing library for a file that any
 * spreadsheet program can export in one step.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportAllotmentUseCase {

    /** A year of daily quota across a dozen room types, with headroom. */
    private static final int MAX_ROWS = 6000;

    private static final List<String> ROOM_HEADERS = List.of("room_type", "roomtype", "room", "loai_phong");
    private static final List<String> DATE_HEADERS = List.of("date", "stay_date", "staydate", "ngay");
    private static final List<String> QTY_HEADERS = List.of("allotted", "allotted_qty", "quantity", "qty", "so_phong");
    private static final List<String> CLOSED_HEADERS = List.of("closed", "stop_sell", "stopsell");

    private final RoomAllotmentRepository allotmentRepository;
    private final ProductServiceRepository productServiceRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SystemAuditLogService systemAuditLogService;

    @Transactional
    public AllotmentImportResponse execute(MultipartFile file, OffsetDateTime asOf) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_REQUIRED", "Please choose a CSV file to import.",
                    HttpStatus.BAD_REQUEST);
        }

        List<String> lines = readLines(file);
        if (lines.isEmpty()) {
            throw new BusinessException("FILE_EMPTY", "The file contains no rows.", HttpStatus.BAD_REQUEST);
        }

        String[] header = splitCsv(lines.get(0));
        int roomIdx = columnIndex(header, ROOM_HEADERS);
        int dateIdx = columnIndex(header, DATE_HEADERS);
        int qtyIdx = columnIndex(header, QTY_HEADERS);
        int closedIdx = columnIndex(header, CLOSED_HEADERS);

        if (roomIdx < 0 || dateIdx < 0 || qtyIdx < 0) {
            throw new BusinessException("HEADER_MISSING",
                    "The first row must name the columns. Expected a room type column ("
                            + String.join(" / ", ROOM_HEADERS) + "), a date column ("
                            + String.join(" / ", DATE_HEADERS) + ") and a quantity column ("
                            + String.join(" / ", QTY_HEADERS) + ").",
                    HttpStatus.BAD_REQUEST);
        }

        Map<String, List<ProductServiceEntity>> roomsByName = activeRoomsByFoldedName();
        OffsetDateTime effectiveAsOf = asOf != null ? asOf : OffsetDateTime.now();
        UserEntity actor = resolveActorQuietly();
        UUID actorId = actor != null ? actor.getUserId() : null;

        List<AllotmentImportResponse.RejectedRow> rejected = new ArrayList<>();
        Map<ProductServiceEntity, Integer> importedPerProduct = new java.util.LinkedHashMap<>();
        int imported = 0;

        for (int i = 1; i < lines.size(); i++) {
            String raw = lines.get(i);
            int lineNumber = i + 1;
            String[] cells = splitCsv(raw);

            try {
                String roomName = cell(cells, roomIdx);
                if (roomName.isBlank()) {
                    rejected.add(reject(lineNumber, raw, "No room type given"));
                    continue;
                }

                List<ProductServiceEntity> matches = roomsByName.getOrDefault(fold(roomName), List.of());
                if (matches.isEmpty()) {
                    rejected.add(reject(lineNumber, raw,
                            "No active room type named \"" + roomName + "\""));
                    continue;
                }
                if (matches.size() > 1) {
                    // Two products sharing a name is a catalogue problem; picking one here would
                    // hide it behind quota that is right half the time.
                    rejected.add(reject(lineNumber, raw,
                            "More than one active room type is named \"" + roomName + "\""));
                    continue;
                }

                LocalDate stayDate = LocalDate.parse(cell(cells, dateIdx).trim());

                boolean closed = parseClosed(cell(cells, closedIdx));
                String qtyText = cell(cells, qtyIdx).trim();
                int quantity;
                if (qtyText.isEmpty() && closed) {
                    // A stop-sell row need not carry a number; nothing is sellable either way.
                    quantity = 0;
                } else {
                    quantity = Integer.parseInt(qtyText);
                    if (quantity < 0) {
                        rejected.add(reject(lineNumber, raw, "Quantity cannot be negative"));
                        continue;
                    }
                }

                allotmentRepository.upsertNight(matches.get(0).getProductId(), stayDate, quantity,
                        closed, null, effectiveAsOf, actorId);
                importedPerProduct.merge(matches.get(0), 1, Integer::sum);
                imported++;

            } catch (DateTimeParseException e) {
                rejected.add(reject(lineNumber, raw, "Date must be in YYYY-MM-DD form"));
            } catch (NumberFormatException e) {
                rejected.add(reject(lineNumber, raw, "Quantity must be a whole number"));
            } catch (Exception e) {
                rejected.add(reject(lineNumber, raw, "Could not be read: " + e.getMessage()));
            }
        }

        // Per room type rather than one row for the file: system_audit_logs requires an
        // entity_id, and a file spanning several room types has no single subject to name.
        importedPerProduct.forEach((product, nightCount) ->
                systemAuditLogService.log("ROOM_ALLOTMENT", "PRODUCT", product.getProductId(),
                        "ALLOTMENT_IMPORTED", actor, null, String.valueOf(nightCount),
                        "%s: %d night(s) imported from %s"
                                .formatted(product.getName(), nightCount, file.getOriginalFilename())));

        log.info("Allotment import from {}: {} imported, {} rejected",
                file.getOriginalFilename(), imported, rejected.size());

        return AllotmentImportResponse.builder()
                .rowsRead(lines.size() - 1)
                .nightsImported(imported)
                .rowsRejected(rejected.size())
                .rejected(rejected)
                .build();
    }

    private List<String> readLines(MultipartFile file) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                // Excel writes a UTF-8 BOM, which would otherwise become part of the first
                // column name and make every header lookup fail on an apparently correct file.
                if (lines.isEmpty() && line.startsWith("﻿")) {
                    line = line.substring(1);
                }
                lines.add(line);
                if (lines.size() > MAX_ROWS) {
                    throw new BusinessException("FILE_TOO_LARGE",
                            "The file has more than " + MAX_ROWS + " rows. Split it and import in parts.",
                            HttpStatus.BAD_REQUEST);
                }
            }
        } catch (IOException e) {
            throw new BusinessException("FILE_UNREADABLE",
                    "The file could not be read: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return lines;
    }

    /** Grouped rather than mapped so a duplicated name is visible instead of silently overwritten. */
    private Map<String, List<ProductServiceEntity>> activeRoomsByFoldedName() {
        Map<String, List<ProductServiceEntity>> byName = new HashMap<>();
        for (ProductServiceEntity product : productServiceRepository.findByCategory(ProductCategory.ROOM)) {
            if (product.getStatus() != ProductStatus.ACTIVE || product.getName() == null) {
                continue;
            }
            byName.computeIfAbsent(fold(product.getName()), k -> new ArrayList<>()).add(product);
        }
        return byName;
    }

    /**
     * Splits one CSV line, honouring double-quoted fields so a room name containing a comma
     * ("Deluxe, sea view") does not silently shift every later column by one.
     */
    private static String[] splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells.toArray(new String[0]);
    }

    private static int columnIndex(String[] header, List<String> candidates) {
        for (int i = 0; i < header.length; i++) {
            String name = fold(header[i]).replace(' ', '_');
            if (candidates.contains(name)) {
                return i;
            }
        }
        return -1;
    }

    private static String cell(String[] cells, int index) {
        return index >= 0 && index < cells.length ? cells[index].trim() : "";
    }

    private static boolean parseClosed(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("1") || v.equals("y") || v.equals("yes") || v.equals("true") || v.equals("x");
    }

    private static String fold(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static AllotmentImportResponse.RejectedRow reject(int line, String content, String reason) {
        return AllotmentImportResponse.RejectedRow.builder()
                .line(line)
                .content(content.length() > 200 ? content.substring(0, 200) + "..." : content)
                .reason(reason)
                .build();
    }

    private UserEntity resolveActorQuietly() {
        try {
            return currentUserProvider.resolve(null);
        } catch (Exception e) {
            log.warn("Could not resolve actor for allotment import: {}", e.getMessage());
            return null;
        }
    }
}
