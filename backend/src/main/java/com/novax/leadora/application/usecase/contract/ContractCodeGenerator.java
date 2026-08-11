package com.novax.leadora.application.usecase.contract;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractCodeGenerator {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Generates a unique contract code following the pattern HD-{year}-{sequence}
     * (e.g. HD-2026-000001). This method runs in a REQUIRES_NEW transaction to
     * avoid holding locks on the contract_sequence table if the parent transaction delays.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateCode() {
        int year = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).getYear();
        
        String sql = """
            INSERT INTO contract_sequence (year, next_val)
            VALUES (?, 2)
            ON CONFLICT (year)
            DO UPDATE SET next_val = contract_sequence.next_val + 1
            RETURNING next_val - 1
            """;
        
        Integer seq = jdbcTemplate.queryForObject(sql, Integer.class, year);
        if (seq == null) {
            seq = 1;
        }
        
        String formatted = String.format("HD-%d-%06d", year, seq);
        log.info("Generated contract code: {}", formatted);
        return formatted;
    }
}
