import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../user/data/user_models.dart';
import '../../data/task_models.dart';
import '../../data/task_repository.dart';
import '../providers/task_permissions.dart';
import '../providers/task_providers.dart';
import '../widgets/activity_type_selector.dart';
import '../widgets/task_entity_picker.dart';
import 'task_list_screen.dart' show TaskAssigneePicker;

/// What the form is doing. All three share the same core fields; edit adds
/// status + result note, resign adds the reassignment rules and note.
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
    // BR-18: only a manager may hand a task over. The detail menu already hides
    // the entry, but the route can also be reached directly — refuse there too
    // rather than let the user fill in a form the server will reject.
    if (mode == TaskFormMode.resign &&
        !ref.watch(taskPermissionsProvider).canReassign) {
      return const _AccessDenied(
        message: 'Only a manager can reassign a follow-up task.',
      );
    }

    if (initial != null) return TaskFormScreen(mode: mode, task: initial);
    final async = ref.watch(taskDetailProvider(taskId));
    return async.when(
      data: (task) => TaskFormScreen(mode: mode, task: task),
      loading: () =>
          const Scaffold(body: Center(child: CircularProgressIndicator())),
      error: (error, _) => _AccessDenied(
        message: error is AppException ? error.message : 'Could not load the task.',
      ),
    );
  }
}

class _AccessDenied extends StatelessWidget {
  const _AccessDenied({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.xxl),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.lock_outline_rounded,
                size: AppIconSize.hero,
                color: theme.colorScheme.onSurfaceVariant,
              ),
              const SizedBox(height: AppSpacing.lg),
              Text(
                message,
                textAlign: TextAlign.center,
                style: theme.textTheme.bodyLarge,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// UC-10.1 / UC-10.4 / UC-10.7 — one form for creating, editing and resigning a
/// follow-up task.
///
/// The three modes differ only in which fields are offered, and the role decides
/// which of those are editable: a manager assigns work to anyone and can move a
/// task's linked record, while a Sales Staff raises tasks for themselves and
/// leaves the ownership fields alone. Those rules are read from
/// [TaskPermissions], which mirrors the server policy — the form never invents
/// its own.
class TaskFormScreen extends ConsumerStatefulWidget {
  const TaskFormScreen({super.key, required this.mode, this.task})
    : assert(
        mode == TaskFormMode.create || task != null,
        'edit/resign require the source task',
      );

  final TaskFormMode mode;
  final Task? task;

  @override
  ConsumerState<TaskFormScreen> createState() => _TaskFormScreenState();
}

class _TaskFormScreenState extends ConsumerState<TaskFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final _title = TextEditingController();
  final _description = TextEditingController();
  final _resultNote = TextEditingController();
  final _resignNote = TextEditingController();
  final _primaryContactName = TextEditingController();
  final _primaryContactPhone = TextEditingController();

  TaskActivityType _activityType = TaskActivityType.followUp;
  TaskPriority _priority = TaskPriority.medium;
  TaskStatus? _status; // edit only
  String? _assigneeId;
  String? _assigneeName;
  DateTime? _startAt;
  DateTime? _endAt;
  TaskEntityLink? _entity;

  bool _submitting = false;
  bool _autovalidate = false;

  bool get _isCreate => widget.mode == TaskFormMode.create;
  bool get _isEdit => widget.mode == TaskFormMode.edit;
  bool get _isResign => widget.mode == TaskFormMode.resign;

  @override
  void initState() {
    super.initState();
    final t = widget.task;

    if (t == null) {
      // New task: it is yours until somebody with the authority says otherwise.
      // Staff can't change this; a manager can pick anyone.
      final me = ref.read(taskPermissionsProvider);
      _assigneeId = me.userId;
      _assigneeName = 'You';
      return;
    }

    _title.text = t.title;
    _activityType = t.activityType;
    _description.text = t.description ?? '';
    _priority = t.priority;
    _status = t.status;
    _resultNote.text = t.resultNote ?? '';
    _primaryContactName.text = t.primaryContactName ?? '';
    _primaryContactPhone.text = t.primaryContactPhone ?? '';
    _startAt = t.startAt;
    _endAt = t.endAt;

    if (t.leadId != null) {
      _entity = TaskEntityLink(
        leadId: t.leadId,
        name: t.leadName ?? 'Lead',
        phone: t.leadPhone,
        email: t.leadEmail,
        companyName: t.leadCompanyName,
      );
    } else if (t.customerId != null) {
      _entity = TaskEntityLink(
        customerId: t.customerId,
        name: t.customerName ?? 'Customer',
        phone: t.customerPhone,
        email: t.customerEmail,
        companyName: t.customerCompanyName,
      );
    } else if (t.dealId != null) {
      _entity = TaskEntityLink(dealId: t.dealId, name: t.dealName ?? 'Deal');
    }

    if (_isResign) {
      // A resign hands the follow-up to someone else — force a fresh pick.
      _assigneeId = null;
      _assigneeName = null;
      // Web defaults the new window to tomorrow 09:00–10:00 when unset.
      _startAt ??= _at(_today().add(const Duration(days: 1)), 9);
      _endAt ??= _at(_today().add(const Duration(days: 1)), 10);
    } else {
      _assigneeId = t.assignedUserId;
      _assigneeName = t.assignedUserName;
    }
  }

  @override
  void dispose() {
    _title.dispose();
    _description.dispose();
    _resultNote.dispose();
    _resignNote.dispose();
    _primaryContactName.dispose();
    _primaryContactPhone.dispose();
    super.dispose();
  }

  String get _screenTitle => switch (widget.mode) {
    TaskFormMode.create => 'New task',
    TaskFormMode.edit => 'Edit task',
    TaskFormMode.resign => 'Reassign follow-up',
  };

  String get _submitLabel => switch (widget.mode) {
    TaskFormMode.create => 'Create task',
    TaskFormMode.edit => 'Save changes',
    TaskFormMode.resign => 'Reassign follow-up',
  };

  // ── Date helpers ────────────────────────────────────────────────────────────

  static DateTime _today() {
    final now = DateTime.now();
    return DateTime(now.year, now.month, now.day);
  }

  static DateTime _at(DateTime day, int hour, [int minute = 0]) =>
      DateTime(day.year, day.month, day.day, hour, minute);

  /// Web `applyDatePreset` — moves both start and end to the chosen day,
  /// keeping the already-picked times (defaults 09:00 → 10:00).
  void _applyPreset(int days) {
    final day = _today().add(Duration(days: days));
    setState(() {
      _startAt = _at(day, _startAt?.hour ?? 9, _startAt?.minute ?? 0);
      _endAt = _at(day, _endAt?.hour ?? 10, _endAt?.minute ?? 0);
    });
  }

  bool _presetSelected(int days) {
    final s = _startAt;
    if (s == null) return false;
    final day = _today().add(Duration(days: days));
    return s.year == day.year && s.month == day.month && s.day == day.day;
  }

  Future<void> _pickDateTime({required bool isStart}) async {
    final now = DateTime.now();
    final fallback = isStart
        ? (_startAt ?? _at(_today(), 9))
        : (_endAt ?? _startAt?.add(const Duration(hours: 1)) ?? _at(_today(), 10));
    // Web blocks scheduling a reassignment in the past.
    final firstDate = _isResign ? _today() : DateTime(now.year - 1);
    final initial = fallback.isBefore(firstDate) ? firstDate : fallback;
    final date = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: firstDate,
      lastDate: DateTime(now.year + 5),
    );
    if (date == null || !mounted) return;
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(fallback),
    );
    if (!mounted) return;
    final picked = DateTime(
      date.year,
      date.month,
      date.day,
      time?.hour ?? fallback.hour,
      time?.minute ?? fallback.minute,
    );
    setState(() {
      if (isStart) {
        _startAt = picked;
      } else {
        _endAt = picked;
      }
    });
  }

  // ── Selection handlers ──────────────────────────────────────────────────────

  /// The activity type is its own field now. Picking one changes *only* that —
  /// it no longer rewrites the title, which is the user's to write.
  void _selectActivityType(TaskActivityType type) {
    setState(() => _activityType = type);
  }

  Future<void> _pickAssignee() async {
    final selected = await showModalBottomSheet<UserSummary>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => TaskAssigneePicker(
        // A resign must hand over to somebody else (web filters the dropdown).
        excludeUserId: _isResign ? widget.task?.assignedUserId : null,
      ),
    );
    if (selected != null) {
      setState(() {
        _assigneeId = selected.userId;
        _assigneeName = selected.fullName;
      });
    }
  }

  Future<void> _pickEntity() async {
    final selected = await showModalBottomSheet<TaskEntityLink>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => const TaskEntityPickerSheet(),
    );
    if (selected == null) return;
    setState(() {
      _entity = selected;
      // Linking a record fills in the contact, and seeds an empty title with the
      // record's name as a starting point. No activity prefix: the type is a
      // field of its own, and the title is just the title.
      _primaryContactName.text = selected.name;
      _primaryContactPhone.text = selected.phone ?? '';
      if (_isCreate && _title.text.trim().isEmpty) {
        _title.text = selected.name;
      }
    });
  }

  void _clearEntity() => setState(() => _entity = null);

  // ── Validation + submit ─────────────────────────────────────────────────────

  String? _validate() {
    if (_title.text.trim().isEmpty) return 'A task title is required.';
    if ((_isCreate || _isEdit) && _assigneeId == null) {
      return 'Please choose an assignee.';
    }
    if (_isCreate && _startAt == null) {
      return 'Please schedule a start date and time.';
    }
    if (_isResign) {
      if (_assigneeId == null) {
        return 'Please select a staff member to reassign to.';
      }
      if (_startAt == null || _endAt == null) {
        return 'Start and end date/time are both required.';
      }
      if (_startAt!.isBefore(_today())) return 'Cannot schedule in the past.';
    }
    if (_startAt != null && _endAt != null && !_endAt!.isAfter(_startAt!)) {
      return 'End time must be after the start time.';
    }
    return null;
  }

  /// Web parity: the reassignment reason is stored as a structured note —
  /// `[Reassigned]` header, who → who, old and new schedule, optional note.
  String _composeResignNote() {
    final t = widget.task!;
    String slot(DateTime? v) =>
        v == null ? '—' : '${Formatters.date(v)} ${Formatters.time(v)}';
    final oldSchedule = t.startAt == null
        ? 'not set'
        : '${slot(t.startAt)} → ${slot(t.endAt)}';
    final lines = [
      '[Reassigned]',
      '${t.assignedUserName ?? 'Unassigned'} → ${_assigneeName ?? 'Unknown'}',
      'Old: $oldSchedule',
      'New: ${slot(_startAt)} → ${slot(_endAt)}',
      if (_resignNote.text.trim().isNotEmpty) 'Note: ${_resignNote.text.trim()}',
    ];
    return lines.join('\n');
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    final error = _validate();
    if (error != null) {
      setState(() => _autovalidate = true);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error)));
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
          await repo.createTask(
            CreateTaskPayload(
              title: _title.text,
              assignedUserId: _assigneeId!,
              priority: _priority,
              activityType: _activityType,
              description: _description.text,
              leadId: _entity?.leadId,
              customerId: _entity?.customerId,
              dealId: _entity?.dealId,
              primaryContactName: _primaryContactName.text,
              primaryContactPhone: _primaryContactPhone.text,
              startAt: _startAt,
              endAt: _endAt,
            ),
          );
          message = 'Task created';
        case TaskFormMode.edit:
          await repo.updateTask(
            widget.task!.taskId,
            UpdateTaskPayload(
              title: _title.text,
              description: _description.text,
              assignedUserId: _assigneeId,
              priority: _priority,
              status: _status,
              activityType: _activityType,
              resultNote: _resultNote.text,
              leadId: _entity?.leadId,
              customerId: _entity?.customerId,
              dealId: _entity?.dealId,
              primaryContactName: _primaryContactName.text,
              primaryContactPhone: _primaryContactPhone.text,
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
              resignNote: _composeResignNote(),
              startAt: _startAt,
              endAt: _endAt,
            ),
          );
          ref.invalidate(taskDetailProvider(widget.task!.taskId));
          message = 'Task reassigned successfully';
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

  // ── Build ───────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final permissions = ref.watch(taskPermissionsProvider);

    // Staff raise tasks for themselves; only a manager routes work to others.
    final canChooseAssignee = permissions.canAssignToOthers;
    // Setting the linked record on a brand-new task is open to everyone; *moving*
    // an existing task's record rewrites its business context, so that is
    // manager-only (UpdateTaskUseCase enforces the same rule).
    final canEditEntity = _isCreate || permissions.canChangeRelatedRecord;

    return Scaffold(
      appBar: AppBar(title: Text(_screenTitle)),
      body: Form(
        key: _formKey,
        autovalidateMode: _autovalidate
            ? AutovalidateMode.onUserInteraction
            : AutovalidateMode.disabled,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(
            AppSpacing.lg,
            AppSpacing.lg,
            AppSpacing.lg,
            AppSpacing.huge,
          ),
          children: [
            if (_isResign) ...[
              _CurrentAssignmentBanner(task: widget.task!),
              const SizedBox(height: AppSpacing.lg),
            ],

            if (!_isResign) ...[
              const _FieldLabel('Activity type'),
              ActivityTypeSelector(
                selected: _activityType,
                enabled: !_submitting,
                onSelected: _selectActivityType,
              ),
              const SizedBox(height: AppSpacing.lg),
            ],

            TextFormField(
              controller: _title,
              enabled: !_submitting,
              maxLength: kTaskTitleMaxLength,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(
                labelText: 'Title *',
                // No "Call:" prefix any more — the activity type above is its own
                // field, so the title is only ever what the task is about.
                hintText: 'e.g. Confirm event headcount',
                prefixIcon: Icon(Icons.title_rounded),
                counterText: '',
              ),
              validator: (v) =>
                  (v == null || v.trim().isEmpty) ? 'Title is required' : null,
            ),
            const SizedBox(height: AppSpacing.lg),
            TextFormField(
              controller: _description,
              enabled: !_submitting,
              maxLines: 3,
              decoration: const InputDecoration(
                labelText: 'Description / goal',
                alignLabelWithHint: true,
                prefixIcon: Icon(Icons.notes_rounded),
              ),
            ),
            const SizedBox(height: AppSpacing.xl),

            // ── Schedule ──
            _FieldLabel(_isCreate || _isResign ? 'Schedule *' : 'Schedule'),
            if (!_isEdit) ...[
              Wrap(
                spacing: AppSpacing.sm,
                runSpacing: AppSpacing.xs,
                children: [
                  for (final (label, days) in const [
                    ('Today', 0),
                    ('+1 day', 1),
                    ('+3 days', 3),
                    ('+1 week', 7),
                  ])
                    ChoiceChip(
                      label: Text(label),
                      selected: _presetSelected(days),
                      onSelected: _submitting ? null : (_) => _applyPreset(days),
                    ),
                ],
              ),
              const SizedBox(height: AppSpacing.sm),
            ],
            _PickerTile(
              icon: Icons.play_arrow_rounded,
              label: _isCreate || _isResign ? 'Start *' : 'Start',
              value: _startAt == null ? null : Formatters.dateTime(_startAt),
              placeholder: 'Not scheduled',
              onTap: _submitting ? null : () => _pickDateTime(isStart: true),
              onClear: _startAt == null || _submitting || _isResign
                  ? null
                  : () => setState(() => _startAt = null),
            ),
            const SizedBox(height: AppSpacing.md),
            _PickerTile(
              icon: Icons.flag_outlined,
              label: _isResign ? 'End *' : 'End',
              value: _endAt == null ? null : Formatters.dateTime(_endAt),
              placeholder: 'No end time',
              onTap: _submitting ? null : () => _pickDateTime(isStart: false),
              onClear: _endAt == null || _submitting || _isResign
                  ? null
                  : () => setState(() => _endAt = null),
            ),
            const SizedBox(height: AppSpacing.xl),

            // ── Priority / status ──
            const _FieldLabel('Priority'),
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
            if (_isEdit) ...[
              const SizedBox(height: AppSpacing.lg),
              DropdownButtonFormField<TaskStatus>(
                initialValue: _status,
                decoration: const InputDecoration(
                  labelText: 'Status',
                  prefixIcon: Icon(Icons.flaky_rounded),
                ),
                items: [
                  const DropdownMenuItem(
                    value: TaskStatus.open,
                    child: Text('Open'),
                  ),
                  const DropdownMenuItem(
                    value: TaskStatus.completed,
                    child: Text('Completed'),
                  ),
                  DropdownMenuItem(
                    value: TaskStatus.cancelled,
                    // Web parity: a completed task must be reopened before it
                    // can be cancelled.
                    enabled: widget.task!.status != TaskStatus.completed,
                    child: Text(
                      widget.task!.status == TaskStatus.completed
                          ? 'Cancelled (reopen first)'
                          : 'Cancelled',
                    ),
                  ),
                ],
                onChanged: _submitting
                    ? null
                    : (v) => setState(() => _status = v ?? _status),
              ),
            ],
            const SizedBox(height: AppSpacing.xl),

            // ── People & links ──
            if (canChooseAssignee)
              _PickerTile(
                icon: Icons.person_outline_rounded,
                label: _isResign ? 'Reassign to *' : 'Assignee *',
                value: _assigneeName,
                placeholder: _isResign ? 'Choose a staff member' : 'Choose a user',
                onTap: _submitting ? null : _pickAssignee,
              )
            else
              _ReadOnlyField(
                icon: Icons.person_outline_rounded,
                label: 'Assigned to',
                value: _assigneeName ?? 'You',
                hint: 'Only a manager can assign this to someone else.',
              ),

            if (!_isResign) ...[
              const SizedBox(height: AppSpacing.md),
              _EntityLinkTile(
                entity: _entity,
                enabled: !_submitting && canEditEntity,
                editable: canEditEntity,
                onPick: _pickEntity,
                onClear: _clearEntity,
              ),
              const SizedBox(height: AppSpacing.md),
              const _FieldLabel('Primary contact'),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: TextFormField(
                      controller: _primaryContactName,
                      enabled: !_submitting,
                      textCapitalization: TextCapitalization.words,
                      decoration: const InputDecoration(
                        labelText: 'Contact name',
                        isDense: true,
                      ),
                    ),
                  ),
                  const SizedBox(width: AppSpacing.md),
                  Expanded(
                    child: TextFormField(
                      controller: _primaryContactPhone,
                      enabled: !_submitting,
                      keyboardType: TextInputType.phone,
                      decoration: const InputDecoration(
                        labelText: 'Phone',
                        isDense: true,
                      ),
                    ),
                  ),
                ],
              ),
            ],

            if (_isEdit) ...[
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _resultNote,
                enabled: !_submitting,
                maxLines: 3,
                decoration: const InputDecoration(
                  labelText: 'Result / notes',
                  hintText: 'Outcome or notes after completing this task',
                  alignLabelWithHint: true,
                  prefixIcon: Icon(Icons.fact_check_outlined),
                ),
              ),
            ],
            if (_isResign) ...[
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _resignNote,
                enabled: !_submitting,
                maxLines: 2,
                decoration: const InputDecoration(
                  labelText: 'Reason / note',
                  hintText: 'Why the task is being handed over',
                  alignLabelWithHint: true,
                  prefixIcon: Icon(Icons.swap_horiz_rounded),
                ),
              ),
            ],

            const SizedBox(height: AppSpacing.xxl),
            FilledButton(
              onPressed: _submitting ? null : _submit,
              style: FilledButton.styleFrom(
                minimumSize: const Size.fromHeight(52),
              ),
              child: _submitting
                  ? const SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(strokeWidth: 2.5),
                    )
                  : Text(_submitLabel),
            ),
            const SizedBox(height: AppSpacing.md),
            TextButton(
              onPressed: _submitting ? null : () => context.pop(),
              style: TextButton.styleFrom(
                minimumSize: const Size.fromHeight(48),
                foregroundColor: theme.colorScheme.onSurfaceVariant,
              ),
              child: const Text('Cancel'),
            ),
          ],
        ),
      ),
    );
  }
}

/// One consistent heading for every field group in the form.
class _FieldLabel extends StatelessWidget {
  const _FieldLabel(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: Text(text, style: Theme.of(context).textTheme.labelLarge),
    );
  }
}

/// A field the current role isn't allowed to change: shows the value and says
/// why it's fixed, instead of a disabled control the user pokes at in vain.
class _ReadOnlyField extends StatelessWidget {
  const _ReadOnlyField({
    required this.icon,
    required this.label,
    required this.value,
    required this.hint,
  });

  final IconData icon;
  final String label;
  final String value;
  final String hint;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        InputDecorator(
          decoration: InputDecoration(
            labelText: label,
            prefixIcon: Icon(icon),
            suffixIcon: Icon(
              Icons.lock_outline_rounded,
              size: AppIconSize.md,
              color: scheme.onSurfaceVariant,
            ),
            filled: true,
            fillColor: scheme.surfaceContainerLow,
          ),
          child: Text(
            value,
            style: theme.textTheme.bodyLarge?.copyWith(
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.only(
            top: AppSpacing.xs,
            left: AppSpacing.md,
          ),
          child: Text(
            hint,
            style: theme.textTheme.bodySmall?.copyWith(
              color: scheme.onSurfaceVariant,
            ),
          ),
        ),
      ],
    );
  }
}

/// "Link to entity (optional)" — selected lead/customer/deal chip with remove, or
/// a tappable tile opening [TaskEntityPickerSheet]. When [editable] is false the
/// link is still shown, just not changeable (staff editing their own task).
class _EntityLinkTile extends StatelessWidget {
  const _EntityLinkTile({
    required this.entity,
    required this.enabled,
    required this.editable,
    required this.onPick,
    required this.onClear,
  });

  final TaskEntityLink? entity;
  final bool enabled;
  final bool editable;
  final VoidCallback onPick;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final e = entity;

    if (e == null) {
      return _PickerTile(
        icon: Icons.link_rounded,
        label: 'Linked deal / lead / customer',
        value: null,
        placeholder: editable ? 'Not linked (optional)' : 'Not linked',
        onTap: enabled ? onPick : null,
      );
    }

    final (avatarBg, avatarFg) = switch (e.kind) {
      TaskEntityKind.lead => (scheme.primaryContainer, scheme.onPrimaryContainer),
      TaskEntityKind.customer => (
        scheme.tertiaryContainer,
        scheme.onTertiaryContainer,
      ),
      TaskEntityKind.deal => (
        scheme.secondaryContainer,
        scheme.onSecondaryContainer,
      ),
    };
    final kindLabel = switch (e.kind) {
      TaskEntityKind.lead => 'Lead',
      TaskEntityKind.customer => 'Customer',
      TaskEntityKind.deal => 'Deal',
    };

    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.md,
        vertical: AppSpacing.sm,
      ),
      decoration: BoxDecoration(
        color: scheme.primaryContainer.withValues(alpha: 0.35),
        borderRadius: BorderRadius.circular(AppRadii.md),
        border: Border.all(color: scheme.primary.withValues(alpha: 0.35)),
      ),
      child: Row(
        children: [
          CircleAvatar(
            radius: 16,
            backgroundColor: avatarBg,
            child: e.isDeal
                ? Icon(Icons.handshake_rounded, size: AppIconSize.sm, color: avatarFg)
                : Text(
                    Formatters.initials(e.name),
                    style: theme.textTheme.labelSmall?.copyWith(
                      fontWeight: FontWeight.w700,
                      color: avatarFg,
                    ),
                  ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  e.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
                Text(
                  '$kindLabel${e.detail.isEmpty ? '' : ' · ${e.detail}'}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
          if (editable) ...[
            IconButton(
              tooltip: 'Change link',
              visualDensity: VisualDensity.compact,
              icon: const Icon(Icons.swap_horiz_rounded, size: AppIconSize.lg),
              onPressed: enabled ? onPick : null,
            ),
            IconButton(
              tooltip: 'Remove link',
              visualDensity: VisualDensity.compact,
              icon: const Icon(Icons.close_rounded, size: AppIconSize.lg),
              onPressed: enabled ? onClear : null,
            ),
          ] else
            Padding(
              padding: const EdgeInsets.only(right: AppSpacing.sm),
              child: Icon(
                Icons.lock_outline_rounded,
                size: AppIconSize.md,
                color: scheme.onSurfaceVariant,
              ),
            ),
        ],
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
      borderRadius: BorderRadius.circular(AppRadii.md),
      onTap: onTap,
      child: InputDecorator(
        decoration: InputDecoration(
          labelText: label,
          prefixIcon: Icon(icon),
          suffixIcon: hasValue && onClear != null
              ? IconButton(
                  icon: const Icon(Icons.close_rounded, size: AppIconSize.md),
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

/// Amber "current assignee + old schedule" banner shown on the resign form,
/// matching the web reassign modal's context block.
class _CurrentAssignmentBanner extends StatelessWidget {
  const _CurrentAssignmentBanner({required this.task});

  final Task task;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = theme.brightness == Brightness.dark;
    const amber = AppColors.warning;
    final fg = dark
        ? HSLColor.fromColor(amber).withLightness(0.75).toColor()
        : HSLColor.fromColor(amber).withLightness(0.28).toColor();
    final schedule = task.startAt == null
        ? 'No schedule set'
        : '${Formatters.dateTime(task.startAt)} → ${Formatters.dateTime(task.endAt)}';
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: amber.withValues(alpha: dark ? 0.16 : 0.08),
        borderRadius: BorderRadius.circular(AppRadii.md),
        border: Border.all(color: amber.withValues(alpha: 0.3)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.swap_horiz_rounded, size: AppIconSize.md, color: fg),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Currently assigned to ${task.assignedUserName ?? 'nobody'}',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: fg,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: AppSpacing.xxs),
                Text(
                  '$schedule\nReassigning creates a new follow-up task for the chosen staff member.',
                  style: theme.textTheme.bodySmall?.copyWith(color: fg),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
