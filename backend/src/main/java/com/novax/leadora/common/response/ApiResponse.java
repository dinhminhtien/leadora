package com.novax.leadora.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private String errorCode;
    private String details;
    private T data;
    private String timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("Success")
                .data(data)
                .timestamp(OffsetDateTime.now().toString())
                .build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(OffsetDateTime.now().toString())
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(OffsetDateTime.now().toString())
                .build();
    }

    public static <T> ApiResponse<T> businessError(String errorCode, String message, String details) {
        return ApiResponse.<T>builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .details(details)
                .timestamp(OffsetDateTime.now().toString())
                .build();
    }

    public static <T> ApiResponse<T> systemError() {
        return systemError(null);
    }

    /**
     * A genuine server fault, carrying a reference that ties it to the logged stack trace.
     *
     * <p>The message stays deliberately vague — a real 500 must not leak internals. The reference
     * is what makes it actionable: without one, "please contact your Admin" leaves the user with
     * nothing to report and the Admin with nothing to search for.
     *
     * @param reference short correlation id, or {@code null} when none was generated
     */
    public static <T> ApiResponse<T> systemError(String reference) {
        return ApiResponse.<T>builder()
                .success(false)
                .errorCode("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred. Please try again later.")
                .details(reference)
                .timestamp(OffsetDateTime.now().toString())
                .build();
    }
}
