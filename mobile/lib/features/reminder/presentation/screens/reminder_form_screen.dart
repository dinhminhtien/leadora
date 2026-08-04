import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../task/presentation/screens/task_list_screen.dart' show TaskAssigneePicker;
import '../../../user/data/user_models.dart';
import '../../data/reminder_models.dart';
import '../../data/reminder_repository.dart';
import '../providers/reminder_providers.dart';
import '../widgets/reminder_entity_picker.dart';

/// What the form is doing. Create requires the linked record (`relatedEntity`
/// / `relatedId`, fixed for the reminder's lifetime); edit offers everything
/// `UpdateReminderRequest` accepts instead.
enum ReminderFormMode { create, edit }

/// Guards the edit route: unlike most other modules, there is no
/// `GET /reminders/{id}` endpoint to refetch from, so editing only works when
/// the list screen handed the tapped [Reminder] over via go_router `extra`.
/// If that was dropped — e.g. the OS killed and restored the process, which
/// go_router warns about for non-codec `extra` — this sends the user back
/// instead of crashing on a bad cast.
class ReminderEditGuard extends StatelessWidget {
  const ReminderEditGuard({super.key, required this.extra});

  final Object? extra;

  @override
  Widget build(BuildContext context) {
    final reminder = extra;
    if (reminder is! Reminder) {
      final theme = Theme.of(context);
      return Scaffold(
        appBar: AppBar(title: const Text('Edit reminder')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.xxl),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  Icons.error_outline_rounded,
                  size: AppIconSize.hero,
                  color: theme.colorScheme.onSurfaceVariant,
                ),
                const SizedBox(height: AppSpacing.lg),
                Text(
                  'Open this reminder from the list to edit it.',
                  textAlign: TextAlign.center,
                  style: theme.textTheme.bodyLarge,
                ),
              ],
            ),
          ),
        ),
      );
    }
    return ReminderFormScreen(mode: ReminderFormMode.edit, reminder: reminder);
  }
}

/// UC-16.1 / UC-16.3 — create a manual reminder, or edit one.
class ReminderFormScreen extends ConsumerStatefulWidget {
  const ReminderFormScreen({super.key, required this.mode, this.reminder})
    : assert(
        mode == ReminderFormMode.create || reminder != null,
        'edit requires the source reminder',
      );

  final ReminderFormMode mode;
  final Reminder? reminder;

  @override
  ConsumerState<ReminderFormScreen> createState() => _ReminderFormScreenState();
}

class _ReminderFormScreenState extends ConsumerState<ReminderFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final _title = TextEditingController();
  final _description = TextEditingController();

  DateTime? _remindAt;
  ReminderPriority _priority = ReminderPriority.medium;
  ReminderRelatedEntityLink? _relatedEntity; // create only
  String? _assigneeId; // create only
  String? _assigneeName; // create only
  bool _markAsDone = false; // edit only
  bool _forceIfDone = false; // edit only

  bool _submitting = false;
  bool _autovalidate = false;

  bool get _isCreate => widget.mode == ReminderFormMode.create;
  bool get _isEdit => widget.mode == ReminderFormMode.edit;
  bool get _isAlreadyDone =>
      _isEdit && widget.reminder!.status == ReminderStatus.done;

  @override
  void initState() {
    super.initState();
    final r = widget.reminder;
    if (r == null) return; // create: everything starts blank/default
    _title.text = r.title;
    _description.text = r.description ?? '';
    _remindAt = r.remindAt;
    _priority = r.priority;
  }

  @override
  void dispose() {
    _title.dispose();
    _description.dispose();
    super.dispose();
  }

  String get _screenTitle =>
      _isCreate ? 'New reminder' : 'Edit reminder';

  String get _submitLabel =>
      _isCreate ? 'Create reminder' : 'Save changes';

  // ── Date/time picker ────────────────────────────────────────────────────

  Future<void> _pickRemindAt() async {
    final now = DateTime.now();
    final fallback = _remindAt ?? now.add(const Duration(hours: 1));
    final initial = fallback.isBefore(now) ? now : fallback;
    final date = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: now,
      lastDate: DateTime(now.year + 5),
    );
    if (date == null || !mounted) return;
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(fallback),
    );
    if (!mounted) return;
    setState(() {
      _remindAt = DateTime(
        date.year,
        date.month,
        date.day,
        time?.hour ?? fallback.hour,
        time?.minute ?? fallback.minute,
      );
    });
  }

  // ── Related entity / assignee pickers (create only) ─────────────────────

  Future<void> _pickRelatedEntity() async {
    final selected = await showModalBottomSheet<ReminderRelatedEntityLink>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => const ReminderEntityPickerSheet(),
    );
    if (selected != null) setState(() => _relatedEntity = selected);
  }

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

  // ── Validation + submit ──────────────────────────────────────────────────

  String? _validate() {
    if (_title.text.trim().isEmpty) return 'A title is required.';
    if (_isCreate) {
      if (_remindAt == null) return 'Please choose a due date and time.';
      if (_relatedEntity == null) {
        return 'Please link this reminder to a lead, deal, quotation, '
            'booking or deposit.';
      }
    }
    // E4 (create) / INVALID_DEADLINE (edit): the due date must be in the
    // future whenever it is set/changed.
    final original = widget.reminder?.remindAt;
    final changedDueDate =
        _remindAt != null &&
        (original == null || !_remindAt!.isAtSameMomentAs(original));
    if (changedDueDate && !_remindAt!.isAfter(DateTime.now())) {
      return 'Due date/time must be in the future.';
    }
    if (_isAlreadyDone && !_forceIfDone) {
      return 'This reminder is already completed. Check "Update anyway" '
          'to change it.';
    }
    return null;
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
    final repo = ref.read(reminderRepositoryProvider);

    try {
      final String message;
      switch (widget.mode) {
        case ReminderFormMode.create:
          await repo.createReminder(
            CreateReminderPayload(
              title: _title.text,
              remindAt: _remindAt!,
              relatedEntity: _relatedEntity!.type,
              relatedId: _relatedEntity!.id,
              description: _description.text,
              priority: _priority,
              assignedUserId: _assigneeId,
            ),
          );
          message = 'Reminder created';
        case ReminderFormMode.edit:
          final r = widget.reminder!;
          final title = _title.text.trim();
          final description = _description.text.trim();
          final remindAtChanged =
              _remindAt != null &&
              (r.remindAt == null || !_remindAt!.isAtSameMomentAs(r.remindAt!));
          await repo.updateReminder(
            r.reminderId,
            UpdateReminderPayload(
              title: title != r.title ? title : null,
              description: description != (r.description ?? '')
                  ? description
                  : null,
              remindAt: remindAtChanged ? _remindAt : null,
              priority: _priority != r.priority ? _priority : null,
              markAsDone: _markAsDone ? true : null,
              forceIfDone: _forceIfDone,
            ),
          );
          message = 'Reminder updated';
      }

      // Reload the list if it is currently alive.
      if (ref.exists(reminderListControllerProvider)) {
        await ref.read(reminderListControllerProvider.notifier).refresh();
      }
      if (!mounted) return;
      messenger.showSnackBar(SnackBar(content: Text(message)));
      router.pop();
    } on AppException catch (e) {
      if (mounted) setState(() => _submitting = false);
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  // ── Build ─────────────────────────────────────────────────────────────────

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
            if (_isAlreadyDone) ...[
              _CompletedBanner(
                forceIfDone: _forceIfDone,
                onChanged: (v) => setState(() => _forceIfDone = v),
              ),
              const SizedBox(height: AppSpacing.lg),
            ],

            TextFormField(
              controller: _title,
              enabled: !_submitting,
              maxLength: 255,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(
                labelText: 'Title *',
                hintText: 'e.g. Follow up on quotation response',
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
                labelText: 'Description',
                hintText: 'Additional context or notes',
                alignLabelWithHint: true,
                prefixIcon: Icon(Icons.notes_rounded),
              ),
            ),
            const SizedBox(height: AppSpacing.xl),

            const _FieldLabel('Due date & time *'),
            _PickerTile(
              icon: Icons.schedule_rounded,
              label: 'Remind at',
              value: _remindAt == null ? null : Formatters.dateTime(_remindAt),
              placeholder: 'Choose date & time',
              onTap: _submitting ? null : _pickRemindAt,
            ),
            const SizedBox(height: AppSpacing.xl),

            const _FieldLabel('Priority'),
            SegmentedButton<ReminderPriority>(
              segments: [
                for (final p in ReminderPriority.values)
                  ButtonSegment(value: p, label: Text(p.label)),
              ],
              selected: {_priority},
              onSelectionChanged: _submitting
                  ? null
                  : (s) => setState(() => _priority = s.first),
            ),

            if (_isCreate) ...[
              const SizedBox(height: AppSpacing.xl),
              const _FieldLabel('Linked record *'),
              _PickerTile(
                icon: Icons.link_rounded,
                label: 'Lead / deal / quotation / booking / deposit',
                value: _relatedEntity == null
                    ? null
                    : '${_relatedEntity!.type.label}: ${_relatedEntity!.label}',
                placeholder: 'Choose a record',
                onTap: _submitting ? null : _pickRelatedEntity,
              ),
              const SizedBox(height: AppSpacing.lg),
              const _FieldLabel('Assign to'),
              _PickerTile(
                icon: Icons.person_outline_rounded,
                label: 'Assignee',
                value: _assigneeName,
                placeholder: 'Myself',
                onTap: _submitting ? null : _pickAssignee,
                onClear: _assigneeId == null || _submitting
                    ? null
                    : () => setState(() {
                        _assigneeId = null;
                        _assigneeName = null;
                      }),
              ),
            ],

            if (_isEdit && !_isAlreadyDone) ...[
              const SizedBox(height: AppSpacing.lg),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Mark as done'),
                subtitle: const Text('Completes the reminder immediately'),
                value: _markAsDone,
                onChanged: _submitting
                    ? null
                    : (v) => setState(() => _markAsDone = v),
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

/// Amber "already completed" banner on the edit form — mirrors the web
/// `UpdateReminderModal`'s DONE banner + "Update anyway" checkbox
/// (`forceIfDone`), required by `UpdateReminderUseCase` to change anything on
/// a `DONE` reminder (`REMINDER_ALREADY_DONE`, HTTP 409, otherwise).
class _CompletedBanner extends StatelessWidget {
  const _CompletedBanner({required this.forceIfDone, required this.onChanged});

  final bool forceIfDone;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = theme.brightness == Brightness.dark;
    final amber = theme.colorScheme.tertiary;
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: amber.withValues(alpha: dark ? 0.16 : 0.08),
        borderRadius: BorderRadius.circular(AppRadii.md),
        border: Border.all(color: amber.withValues(alpha: 0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.check_circle_rounded, size: AppIconSize.md, color: amber),
              const SizedBox(width: AppSpacing.sm),
              Expanded(
                child: Text(
                  'This reminder is already completed.',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          CheckboxListTile(
            contentPadding: EdgeInsets.zero,
            controlAffinity: ListTileControlAffinity.leading,
            title: const Text('Update anyway'),
            value: forceIfDone,
            onChanged: (v) => onChanged(v ?? false),
          ),
        ],
      ),
    );
  }
}
