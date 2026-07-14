import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';
import '../../../core/network/pagination_response.dart';
import 'task_models.dart';

/// All follow-up task API calls.
class TaskRepository {
  TaskRepository(this._client);

  final ApiClient _client;

  /// UC-24.16 — paged task list, filterable by search / status / priority /
  /// assignee / customer / overdue. Mirrors `GET /tasks` on the backend.
  Future<PaginationResponse<Task>> getTasks({
    String? search,
    String? status,
    String? priority,
    String? assignedUserId,
    String? customerId,
    bool overdue = false,
    int page = 0,
    int size = 20,
  }) {
    return _client.getPaged<Task>(
      ApiPaths.tasks,
      query: {
        if (search != null && search.trim().isNotEmpty) 'search': search.trim(),
        'status': ?status,
        'priority': ?priority,
        'assignedUserId': ?assignedUserId,
        'customerId': ?customerId,
        if (overdue) 'overdue': true,
        'page': page,
        'size': size,
      },
      decodeItem: (item) => Task.fromJson(item as Map<String, dynamic>),
    );
  }

  /// UC-10.1 — create a follow-up task. `POST /tasks`.
  Future<Task> createTask(CreateTaskPayload payload) {
    return _client.post<Task>(
      ApiPaths.tasks,
      data: payload.toJson(),
      decode: (data) => Task.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-10.4 — full edit (title / assignee / priority / schedule / status).
  /// `PUT /tasks/{id}`.
  Future<Task> updateTask(String taskId, UpdateTaskPayload payload) {
    return _client.put<Task>(
      ApiPaths.taskById(taskId),
      data: payload.toJson(),
      decode: (data) => Task.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-10.7 — resign: clone into a new follow-up task. `POST /tasks/{id}/resign`.
  Future<Task> resignTask(String taskId, ResignTaskPayload payload) {
    return _client.post<Task>(
      ApiPaths.taskResign(taskId),
      data: payload.toJson(),
      decode: (data) => Task.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-24.17 — task detail.
  Future<Task> getTask(String taskId) {
    return _client.get<Task>(
      ApiPaths.taskById(taskId),
      decode: (data) => Task.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-24.5 — update progress (status transition + result note).
  Future<Task> updateProgress(
    String taskId, {
    TaskStatus? status,
    String? resultNote,
  }) {
    return _client.put<Task>(
      ApiPaths.taskById(taskId),
      data: {
        if (status != null) 'status': status.wire,
        if (resultNote != null && resultNote.trim().isNotEmpty)
          'resultNote': resultNote.trim(),
      },
      decode: (data) => Task.fromJson(data as Map<String, dynamic>),
    );
  }

  /// UC-24.5 — mark a task done via the dedicated resolve endpoint (also
  /// resolves SLA tracking + cancels reminders server-side).
  Future<Task> resolve(String taskId) {
    return _client.patch<Task>(
      ApiPaths.taskResolve(taskId),
      decode: (data) => Task.fromJson(data as Map<String, dynamic>),
    );
  }
}

final taskRepositoryProvider = Provider<TaskRepository>((ref) {
  return TaskRepository(ref.watch(apiClientProvider));
});
