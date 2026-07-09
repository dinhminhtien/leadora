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
import '../providers/task_providers.dart';
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
    if (initial != null) return TaskFormScreen(mode: mode, task: initial);
    final async = ref.watch(taskDetailProvider(taskId));
    return async.when(
      data: (task) => TaskFormScreen(mode: mode, task: task),
      loading: () =>
          const Scaffold(body: Center(child: CircularProgressIndicator())),
      error: (error, _) => Scaffold(
        appBar: AppBar(),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.xxl),
            child: Text(
              error is AppException
                  ? error.message
                  : 'Could not load the task.',
              textAlign: TextAlign.center,
            ),
          ),
        ),
      ),
    );
  }
}

/// UC-10.1 / UC-10.4 / UC-10.7 — one form for creating, editing and resigning a
/// follow-up task. Field set mirrors the web `CreateTaskDrawer` /
/// `TaskDetailDrawer` edit form / `ReassignFollowUpModal` respectively:
/// activity type, title, description, schedule (with quick presets), priority,
/// status (edit), assignee, linked lead/customer, primary contact, result note
/// (edit) and resign note (resign).
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
    if (t != null) {
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
        : (_endAt ??
              _startAt?.add(const Duration(hours: 1)) ??
              _at(_today(), 10));
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

  bool get _titleIsAuto {
    final t = _title.text;
    return t.trim().isEmpty ||
        TaskActivityType.values.any((a) => t == a.titlePrefix);
  }

  void _selectActivityType(TaskActivityType type) {
    setState(() {
      if (_titleIsAuto) _title.text = type.titlePrefix;
      _activityType = type;
    });
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
      // Web behavior: linking an entity fills the primary contact, and (on
      // create) auto-titles "<Type>: <Name>" while the title is still auto.
      _primaryContactName.text = selected.name;
      _primaryContactPhone.text = selected.phone ?? '';
      if (_isCreate && _titleIsAuto) {
        _title.text = '${_activityType.label}: ${selected.name}';
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
      if (_resignNote.text.trim().isNotEmpty)
        'Note: ${_resignNote.text.trim()}',
    ];
    return lines.join('\n');
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    final error = _validate();
    if (error != null) {
      setState(() => _autovalidate = true);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error)));
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
              description: _description.text,
              leadId: _entity?.leadId,
              customerId: _entity?.customerId,
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
              resultNote: _resultNote.text,
              leadId: _entity?.leadId,
              customerId: _entity?.customerId,
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
              const SizedBox(height: 16),
            ],
            if (!_isResign) ...[
              Text('Activity type', style: theme.textTheme.labelLarge),
              const SizedBox(height: 8),
              _ActivityTypeGrid(
                selected: _activityType,
                enabled: !_submitting,
                onSelected: _selectActivityType,
              ),
              const SizedBox(height: 16),
            ],
            TextFormField(
              controller: _title,
              enabled: !_submitting,
              maxLength: kTaskTitleMaxLength,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(
                labelText: 'Title *',
                hintText: 'e.g. Call: confirm event headcount',
                prefixIcon: Icon(Icons.title_rounded),
                counterText: '',
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
                labelText: 'Description / goal',
                alignLabelWithHint: true,
                prefixIcon: Icon(Icons.notes_rounded),
              ),
            ),
            const SizedBox(height: 20),

            // ── Schedule ──
            Text(
              _isCreate || _isResign ? 'Schedule *' : 'Schedule',
              style: theme.textTheme.labelLarge,
            ),
            const SizedBox(height: 8),
            if (!_isEdit) ...[
              Wrap(
                spacing: 8,
                runSpacing: 4,
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
                      onSelected: _submitting
                          ? null
                          : (_) => _applyPreset(days),
                    ),
                ],
              ),
              const SizedBox(height: 8),
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
            const SizedBox(height: 12),
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
            const SizedBox(height: 20),

            // ── Priority / status ──
            Text('Priority', style: theme.textTheme.labelLarge),
            const SizedBox(height: 8),
            SegmentedButton<TaskPriority>(
              segments: const [
                ButtonSegment(value: TaskPriority.low, label: Text('Low')),
                ButtonSegment(
                  value: TaskPriority.medium,
                  label: Text('Medium'),
                ),
                ButtonSegment(value: TaskPriority.high, label: Text('High')),
              ],
              selected: {_priority},
              onSelectionChanged: _submitting
                  ? null
                  : (s) => setState(() => _priority = s.first),
            ),
            if (_isEdit) ...[
              const SizedBox(height: 16),
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
            const SizedBox(height: 20),

            // ── People & links ──
            _PickerTile(
              icon: Icons.person_outline_rounded,
              label: _isResign ? 'Reassign to *' : 'Assignee *',
              value: _assigneeName,
              placeholder: _isResign
                  ? 'Choose a staff member'
                  : 'Choose a user',
              onTap: _submitting ? null : _pickAssignee,
            ),
            const SizedBox(height: 12),
            if (!_isResign) ...[
              _EntityLinkTile(
                entity: _entity,
                enabled: !_submitting,
                onPick: _pickEntity,
                onClear: _clearEntity,
              ),
              const SizedBox(height: 12),
              Text('Primary contact', style: theme.textTheme.labelLarge),
              const SizedBox(height: 8),
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
                  const SizedBox(width: 12),
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
              const SizedBox(height: 16),
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
              const SizedBox(height: 16),
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
            const SizedBox(height: 28),
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
          ],
        ),
      ),
    );
  }
}

/// The 6 Pipedrive-style activity type chips (3 per row), matching the web
/// create drawer's selector grid.
class _ActivityTypeGrid extends StatelessWidget {
  const _ActivityTypeGrid({
    required this.selected,
    required this.enabled,
    required this.onSelected,
  });

  final TaskActivityType selected;
  final bool enabled;
  final ValueChanged<TaskActivityType> onSelected;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final dark = theme.brightness == Brightness.dark;
    return LayoutBuilder(
      builder: (context, constraints) {
        const gap = 8.0;
        final width = (constraints.maxWidth - gap * 2) / 3;
        return Wrap(
          spacing: gap,
          runSpacing: gap,
          children: [
            for (final type in TaskActivityType.values)
              SizedBox(
                width: width,
                child: Material(
                  color: selected == type
                      ? type.color.withValues(alpha: dark ? 0.24 : 0.10)
                      : scheme.surfaceContainerLow,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(AppRadii.md),
                    side: BorderSide(
                      color: selected == type
                          ? type.color.withValues(alpha: 0.55)
                          : scheme.outlineVariant.withValues(alpha: 0.6),
                    ),
                  ),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(AppRadii.md),
                    onTap: enabled ? () => onSelected(type) : null,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        vertical: 10,
                        horizontal: 4,
                      ),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            type.icon,
                            size: 18,
                            color: selected == type
                                ? type.color
                                : scheme.onSurfaceVariant,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            type.label,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: theme.textTheme.labelSmall?.copyWith(
                              fontWeight: FontWeight.w700,
                              color: selected == type
                                  ? type.color
                                  : scheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
          ],
        );
      },
    );
  }
}

/// "Link to entity (optional)" — selected lead/customer chip with remove, or a
/// tappable tile opening [TaskEntityPickerSheet].
class _EntityLinkTile extends StatelessWidget {
  const _EntityLinkTile({
    required this.entity,
    required this.enabled,
    required this.onPick,
    required this.onClear,
  });

  final TaskEntityLink? entity;
  final bool enabled;
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
        label: 'Linked lead / customer',
        value: null,
        placeholder: 'Not linked (optional)',
        onTap: enabled ? onPick : null,
      );
    }
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
            backgroundColor: e.isLead
                ? scheme.primaryContainer
                : scheme.tertiaryContainer,
            child: Text(
              Formatters.initials(e.name),
              style: theme.textTheme.labelSmall?.copyWith(
                fontWeight: FontWeight.w700,
                color: e.isLead
                    ? scheme.onPrimaryContainer
                    : scheme.onTertiaryContainer,
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
                  '${e.isLead ? 'Lead' : 'Customer'}'
                  '${e.detail.isEmpty ? '' : ' · ${e.detail}'}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
          IconButton(
            tooltip: 'Change link',
            visualDensity: VisualDensity.compact,
            icon: const Icon(Icons.swap_horiz_rounded, size: 20),
            onPressed: enabled ? onPick : null,
          ),
          IconButton(
            tooltip: 'Remove link',
            visualDensity: VisualDensity.compact,
            icon: const Icon(Icons.close_rounded, size: 20),
            onPressed: enabled ? onClear : null,
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
          Icon(Icons.swap_horiz_rounded, size: 18, color: fg),
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
                const SizedBox(height: 2),
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
