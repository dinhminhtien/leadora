package com.novax.leadora.infrastructure.persistence.repository.projection;

import java.util.UUID;

public interface StaffTaskPerformanceProjection {
    UUID getStaffId();
    long getCompletedTasks();
    long getOnTimeTasks();
    long getOverdueTasks();
}
