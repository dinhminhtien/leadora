import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../shared/formatters.dart';
import '../../../user/data/user_models.dart';
import '../../data/task_models.dart';
import '../../data/task_repository.dart';
import '../providers/task_providers.dart';
import 'task_list_screen.dart' show TaskAssigneePicker;

/// What the form is doing. All three share the same fields; only the title,
/// submit label and target endpoint differ.
enum TaskFormMode { create, edit, resign }

/// Resolves the source task for edit/resign. Prefers the [initial] task handed
/// over via go_router `extra` (instant, no network); if that was dropped — e.g.
/// the OS killed and restored the process, which go_router warns about for
/// non-codec `extra` — it refetches by [taskId] so the form still opens instead
/// of tripping [TaskFormScreen]'s assert.
class TaskFormLoader extends ConsumerWidget {
  const TaskFormLoader({
    super.key,
    required this.mode,
    required this.taskId,
    this.initial,
  });

  final TaskFormMode mode;
  final String taskId;
  final Task? initial;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (initial != null) return TaskFormScreen(mode: mode, task: initial);
    final async = ref.watch(taskDetailProvider(taskId));
    return async.when(
      data: (task) => TaskFormScreen(mode: mode, task: task),
      loading: () => const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      ),
      error: (error, _) => Scaffold(
        appBar: AppBar(),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text(
              error is AppException ? error.message : 'Could not load the task.',
              textAlign: TextAlign.center,
            ),
          ),
        ),
      ),
    );
  }
}

/// UC-10.1 / UC-10.4 / UC-10.7 — one form for creating, editing and resigning a
/// follow-up task. For [TaskFormMode.edit] / [TaskFormMode.resign] the caller
/// passes the source [task] (via go_router `extra`) so the form pre-fills.
class TaskFormScreen extends ConsumerStatefulWidget {
  const TaskFormScreen({super.key, required this.mode, this.task})
      : assert(mode == TaskFormMode.create || task != null,
            'edit/resign require the source task');

  final TaskFormMode mode;
  final Task? task;

  @override
  ConsumerState<TaskFormScreen> createState() => _TaskFormScreenState();
}

class _TaskFormScreenState extends ConsumerState<TaskFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final _title = TextEditingController();
  final _description = TextEditingController();
  final _resignNote = TextEditingController();

  TaskPriority _priority = TaskPriority.medium;
  String? _assigneeId;
  String? _assigneeName;
  DateTime? _startAt;
  DateTime? _endAt;

  bool _submitting = false;
  bool _autovalidate = false;

  bool get _isCreate => widget.mode == TaskFormMode.create;
  bool get _isResign => widget.mode == TaskFormMode.resign;

  @override
  void initState() {
    super.initState();
    final t = widget.task;
    if (t != null) {
      _title.text = t.title;
      _description.text = t.description ?? '';
      _priority = t.priority;
      _assigneeId = t.assignedUserId;
      _assigneeName = t.assignedUserName;
      _startAt = t.startAt;
      _endAt = t.endAt;
    }
  }

  @override
  void dispose() {
    _title.dispose();
    _description.dispose();
    _resignNote.dispose();
    super.dispose();
  }

  String get _screenTitle => switch (widget.mode) {
        TaskFormMode.create => 'New task',
        TaskFormMode.edit => 'Edit task',
        TaskFormMode.resign => 'Resign task',
      };

  String get _submitLabel => switch (widget.mode) {
        TaskFormMode.create => 'Create task',
        TaskFormMode.edit => 'Save changes',
        TaskFormMode.resign => 'Create follow-up',
      };

  Future<void> _pickAssignee() async {
    final selected = await showModalBottomSheet<UserSummary>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => const TaskAssigneePicker(),
    );
    if (selected != null) {
      setState(() {
        _assigneeId = selected.userId;
        _assigneeName = selected.fullName;
      });
    }
  }

  Future<void> _pickDateTime({required bool isStart}) async {
    final now = DateTime.now();
    final initial = (isStart ? _startAt : _endAt) ?? now;
    final date = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime(now.year - 1),
      lastDate: DateTime(now.year + 5),
    );
    if (date == null || !mounted) return;
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(initial),
    );
    if (!mounted) return;
    final picked = DateTime(
      date.year,
      date.month,
      date.day,
      time?.hour ?? 0,
      time?.minute ?? 0,
    );
    setState(() {
      if (isStart) {
        _startAt = picked;
      } else {
        _endAt = picked;
      }
    });
  }

  String? _validate() {
    if (_title.text.trim().isEmpty) return 'A task title is required.';
    // Backend requires an assignee on create; resign copies the parent's if
    // left as-is, so an assignee is always present here in practice.
    if (_isCreate && _assigneeId == null) return 'Please choose an assignee.';
    if (_startAt != null && _endAt != null && _endAt!.isBefore(_startAt!)) {
      return 'Due time must be after the start time.';
    }
    return null;
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    final error = _validate();
    if (error != null) {
      setState(() => _autovalidate = true);
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error)));
      return;
    }
    setState(() => _submitting = true);
    final messenger = ScaffoldMessenger.of(context);
    final router = GoRouter.of(context);
    final repo = ref.read(taskRepositoryProvider);

    try {
      final String message;
      switch (widget.mode) {
        case TaskFormMode.create:
          await repo.createTask(CreateTaskPayload(
            title: _title.text,
            assignedUserId: _assigneeId!,
            priority: _priority,
            description: _description.text,
            startAt: _startAt,
            endAt: _endAt,
          ));
          message = 'Task created';
        case TaskFormMode.edit:
          await repo.updateTask(
            widget.task!.taskId,
            UpdateTaskPayload(
              title: _title.text,
              description: _description.text,
              assignedUserId: _assigneeId,
              priority: _priority,
              startAt: _startAt,
              endAt: _endAt,
            ),
          );
          ref.invalidate(taskDetailProvider(widget.task!.taskId));
          message = 'Task updated';
        case TaskFormMode.resign:
          await repo.resignTask(
            widget.task!.taskId,
            ResignTaskPayload(
              title: _title.text,
              description: _description.text,
              priority: _priority,
              assignedUserId: _assigneeId,
              resignNote: _resignNote.text,
              startAt: _startAt,
              endAt: _endAt,
            ),
          );
          message = 'Follow-up task created';
      }

      // Reload the list if it is currently alive, preserving active filters.
      if (ref.exists(taskListControllerProvider)) {
        unawaited(ref.read(taskListControllerProvider.notifier).refresh());
      }
      messenger.showSnackBar(SnackBar(content: Text(message)));
      router.pop();
    } on AppException catch (e) {
      if (mounted) setState(() => _submitting = false);
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(_screenTitle)),
      body: Form(
        key: _formKey,
        autovalidateMode: _autovalidate
            ? AutovalidateMode.onUserInteraction
            : AutovalidateMode.disabled,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 40),
          children: [
            if (_isResign && widget.task?.title != null) ...[
              _ParentBanner(parentTitle: widget.task!.title),
              const SizedBox(height: 16),
            ],
            TextFormField(
              controller: _title,
              enabled: !_submitting,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(
                labelText: 'Title *',
                prefixIcon: Icon(Icons.title_rounded),
              ),
              validator: (v) =>
                  (v == null || v.trim().isEmpty) ? 'Title is required' : null,
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _description,
              enabled: !_submitting,
              maxLines: 3,
              decoration: const InputDecoration(
                labelText: 'Description',
                alignLabelWithHint: true,
                prefixIcon: Icon(Icons.notes_rounded),
              ),
            ),
            const SizedBox(height: 20),
            Text('Priority', style: theme.textTheme.labelLarge),
            const SizedBox(height: 8),
            SegmentedButton<TaskPriority>(
              segments: const [
                ButtonSegment(value: TaskPriority.low, label: Text('Low')),
                ButtonSegment(value: TaskPriority.medium, label: Text('Medium')),
                ButtonSegment(value: TaskPriority.high, label: Text('High')),
              ],
              selected: {_priority},
              onSelectionChanged: _submitting
                  ? null
                  : (s) => setState(() => _priority = s.first),
            ),
            const SizedBox(height: 20),
            _PickerTile(
              icon: Icons.person_outline_rounded,
              label: _isCreate ? 'Assignee *' : 'Assignee',
              value: _assigneeName,
              placeholder: 'Choose a user',
              onTap: _submitting ? null : _pickAssignee,
            ),
            const SizedBox(height: 12),
            _PickerTile(
              icon: Icons.play_arrow_rounded,
              label: 'Start',
              value: _startAt == null ? null : Formatters.dateTime(_startAt),
              placeholder: 'Not scheduled',
              onTap: _submitting ? null : () => _pickDateTime(isStart: true),
              onClear: _startAt == null || _submitting
                  ? null
                  : () => setState(() => _startAt = null),
            ),
            const SizedBox(height: 12),
            _PickerTile(
              icon: Icons.flag_outlined,
              label: 'Due',
              value: _endAt == null ? null : Formatters.dateTime(_endAt),
              placeholder: 'No due date',
              onTap: _submitting ? null : () => _pickDateTime(isStart: false),
              onClear: _endAt == null || _submitting
                  ? null
                  : () => setState(() => _endAt = null),
            ),
            if (_isResign) ...[
              const SizedBox(height: 16),
              TextFormField(
                controller: _resignNote,
                enabled: !_submitting,
                maxLines: 2,
                decoration: const InputDecoration(
                  labelText: 'Resign note',
                  hintText: 'Why the task is being handed over',
                  alignLabelWithHint: true,
                  prefixIcon: Icon(Icons.swap_horiz_rounded),
                ),
              ),
            ],
            const SizedBox(height: 28),
            FilledButton(
              onPressed: _submitting ? null : _submit,
              style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(52)),
              child: _submitting
                  ? const SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(strokeWidth: 2.5))
                  : Text(_submitLabel),
            ),
          ],
        ),
      ),
    );
  }
}

/// A tappable field that looks like a filled text field but opens a picker.
class _PickerTile extends StatelessWidget {
  const _PickerTile({
    required this.icon,
    required this.label,
    required this.value,
    required this.placeholder,
    required this.onTap,
    this.onClear,
  });

  final IconData icon;
  final String label;
  final String? value;
  final String placeholder;
  final VoidCallback? onTap;
  final VoidCallback? onClear;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final hasValue = value != null;
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: onTap,
      child: InputDecorator(
        decoration: InputDecoration(
          labelText: label,
          prefixIcon: Icon(icon),
          suffixIcon: hasValue && onClear != null
              ? IconButton(
                  icon: const Icon(Icons.close_rounded, size: 18),
                  onPressed: onClear,
                )
              : const Icon(Icons.chevron_right_rounded),
        ),
        child: Text(
          hasValue ? value! : placeholder,
          style: theme.textTheme.bodyLarge?.copyWith(
            color: hasValue
                ? theme.colorScheme.onSurface
                : theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ),
    );
  }
}

class _ParentBanner extends StatelessWidget {
  const _ParentBanner({required this.parentTitle});

  final String parentTitle;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Icon(Icons.content_copy_rounded,
              size: 18, color: theme.colorScheme.onSurfaceVariant),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              'Cloning from "$parentTitle" — this creates a new follow-up task.',
              style: theme.textTheme.bodySmall
                  ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
          ),
        ],
      ),
    );
  }
}
