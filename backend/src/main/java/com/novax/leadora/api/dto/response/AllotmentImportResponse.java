package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * The outcome of an allotment import, including everything that did <b>not</b> go in.
 *
 * <p>An importer that reports only a success count is worse than useless here: the rows it
 * quietly dropped are nights that will read as "not published" and route quotations to the
 * Reservation desk for no reason anybody can see. Every rejected line comes back with its number
 * and the reason, so the file can be corrected rather than re-uploaded hopefully.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AllotmentImportResponse {

    private int rowsRead;
    private int nightsImported;
    private int rowsRejected;
    private List<RejectedRow> rejected;

    @Getter
    @Builder
    public static class RejectedRow {
        /** 1-based line number in the uploaded file, header included, so it maps to the editor. */
        private int line;
        private String content;
        private String reason;
    }
}
