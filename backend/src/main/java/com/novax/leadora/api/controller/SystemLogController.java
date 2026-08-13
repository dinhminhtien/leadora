package com.novax.leadora.api.controller;

import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.infrastructure.logging.InMemoryLogAppender;
import com.novax.leadora.infrastructure.logging.LogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/system-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SystemLogController {

    @GetMapping
    public ResponseEntity<ApiResponse<List<LogEntry>>> getSystemLogs(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword
    ) {
        List<LogEntry> logs = InMemoryLogAppender.getLogs().stream()
                .filter(log -> {
                    if (level != null && !level.isBlank() && !"ALL".equalsIgnoreCase(level)) {
                        if (!log.level().equalsIgnoreCase(level)) {
                            return false;
                        }
                    }
                    if (keyword != null && !keyword.isBlank()) {
                        String lowerKeyword = keyword.toLowerCase();
                        boolean matchesMessage = log.message() != null && log.message().toLowerCase().contains(lowerKeyword);
                        boolean matchesLogger = log.loggerName() != null && log.loggerName().toLowerCase().contains(lowerKeyword);
                        boolean matchesException = log.exception() != null && log.exception().toLowerCase().contains(lowerKeyword);
                        boolean matchesUserId = log.userId() != null && log.userId().toLowerCase().contains(lowerKeyword);
                        boolean matchesUserEmail = log.userEmail() != null && log.userEmail().toLowerCase().contains(lowerKeyword);
                        boolean matchesCorrelationId = log.correlationId() != null && log.correlationId().toLowerCase().contains(lowerKeyword);
                        return matchesMessage || matchesLogger || matchesException || matchesUserId || matchesUserEmail || matchesCorrelationId;
                    }
                    return true;
                })
                // Newest first
                .sorted(Comparator.comparing(LogEntry::timestamp).reversed())
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearSystemLogs() {
        InMemoryLogAppender.clear();
        return ResponseEntity.ok(ApiResponse.success(null, "System logs cleared successfully."));
    }
}
