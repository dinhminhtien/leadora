package com.novax.leadora.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatsResponse {
    private long total;
    private long open;
    private long completed;
    private long overdue;
}
