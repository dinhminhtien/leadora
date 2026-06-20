package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.CreateTaskRequest;
import com.novax.leadora.api.dto.request.UpdateTaskRequest;
import com.novax.leadora.api.dto.response.TaskResponse;
import com.novax.leadora.application.usecase.task.CreateTaskUseCase;
import com.novax.leadora.application.usecase.task.GetTaskDetailUseCase;
import com.novax.leadora.application.usecase.task.GetTaskListUseCase;
import com.novax.leadora.application.usecase.task.UpdateTaskUseCase;
import com.novax.leadora.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class TaskController {

    private final CreateTaskUseCase createTaskUseCase;
    private final GetTaskListUseCase getTaskListUseCase;
    private final GetTaskDetailUseCase getTaskDetailUseCase;
    private final UpdateTaskUseCase updateTaskUseCase;

    /** UC-10.1 — Create Follow-up Task */
    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(@Valid @RequestBody CreateTaskRequest request) {
        TaskResponse task = createTaskUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(task, "Task created successfully"));
    }

    /** UC-10.2 / UC-10.5 — View & Search/Filter Follow-up Task List */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<TaskResponse>>> getTasks(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String assignedUserId,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<TaskResponse> tasks = getTaskListUseCase.execute(search, status, priority, assignedUserId, overdue, page, size);
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    /** UC-10.3 — View Follow-up Task Detail */
    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponse>> getTaskDetail(@PathVariable UUID taskId) {
        TaskResponse task = getTaskDetailUseCase.execute(taskId);
        return ResponseEntity.ok(ApiResponse.success(task));
    }

    /** UC-10.4 / UC-10.6 — Update / Reassign Follow-up Task */
    @PutMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskRequest request
    ) {
        TaskResponse task = updateTaskUseCase.execute(taskId, request);
        return ResponseEntity.ok(ApiResponse.success(task, "Task updated successfully"));
    }

}
