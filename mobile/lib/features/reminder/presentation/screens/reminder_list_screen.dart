import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/app_filter_chip.dart';
import '../../../../shared/widgets/app_search_field.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/highlight_glow.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/reminder_models.dart';
import '../providers/reminder_providers.dart';

const _highlightDuration = Duration(seconds: 4);

/// Maps a reminder's `relatedEntity`/`relatedId` to a screen this app can
/// show — mirrors `NotificationListScreen._relatedRoute`.
String? _relatedRoute(Reminder r) {
  final id = r.relatedId;
  if (id == null || id.isEmpty) return null;
  switch (r.relatedEntity?.toUpperCase()) {
    case 'LEAD':
      return Routes.leadDetailPath(id);
    case 'TASK':
      return Routes.taskDetailPath(id);
    case 'QUOTATION':
      return Routes.quotationDetailPath(id);
    case 'DEAL':
      return Routes.dealDetailPath(id);
    default:
      return null;
  }
}

/// UC-16.1–16.5 — Reminders on Mobile: view (own, or for Manager/Admin every
/// staff member's), create, edit, search/filter and dismiss.
///
/// [highlightId], when set (arrives via the notification tap → `?highlight=`
/// deep-link, see `Routes.remindersPath`), flashes and scrolls to the
/// matching reminder once the list loads.
class ReminderListScreen extends ConsumerStatefulWidget {
  const ReminderListScreen({super.key, this.highlightId});

  final String? highlightId;

  @override
  ConsumerState<ReminderListScreen> createState() => _ReminderListScreenState();
}

class _ReminderListScreenState extends ConsumerState<ReminderListScreen> {
  String? _highlightId;
  Timer? _timer;
  final Set<String> _scrolledIds = {};

  @override
  void initState() {
    super.initState();
    _highlightId = widget.highlightId;
    if (_highlightId != null) {
      _timer = Timer(_highlightDuration, () {
        if (mounted) setState(() => _highlightId = null);
      });
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  ReminderListController get _controller =>
      ref.read(reminderListControllerProvider.notifier);

  void _onSearchChanged(String term) =>
      _controller.applyFilters(_controller.filters.copyWith(search: term));

  Future<void> _openSortSheet() async {
    final current = _controller.filters.sortBy;
    final picked = await showModalBottomSheet<ReminderSort>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: RadioGroup<ReminderSort>(
          groupValue: current,
          onChanged: (value) => Navigator.of(sheetContext).pop(value),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (final sort in ReminderSort.values)
                RadioListTile<ReminderSort>(value: sort, title: Text(sort.label)),
              const SizedBox(height: AppSpacing.sm),
            ],
          ),
        ),
      ),
    );
    if (picked != null && mounted) {
      _controller.applyFilters(_controller.filters.copyWith(sortBy: picked));
    }
  }

  Future<void> _openDateRangeSheet() async {
    final result = await showModalBottomSheet<ReminderFilters>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _ReminderDateRangeSheet(initial: _controller.filters),
    );
    if (result != null) _controller.applyFilters(result);
  }

  Future<void> _openCreate() async {
    await context.pushNamed(RouteNames.reminderCreate);
  }

  Future<void> _openEdit(Reminder reminder) async {
    await context.pushNamed(
      RouteNames.reminderEdit,
      pathParameters: {'id': reminder.reminderId},
      extra: reminder,
    );
  }

  @override
  Widget build(BuildContext context) {
    final asyncState = ref.watch(reminderListControllerProvider);
    final permissions = ref.watch(reminderPermissionsProvider);
    final filters = asyncState.valueOrNull?.filters ?? const ReminderFilters();
    final dateRangeCount =
        (filters.remindFrom != null || filters.remindTo != null) ? 1 : 0;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Reminders'),
        actions: [
          IconButton(
            tooltip: 'Sort',
            onPressed: _openSortSheet,
            icon: const Icon(Icons.swap_vert_rounded),
          ),
          IconButton(
            tooltip: 'Due date range',
            onPressed: _openDateRangeSheet,
            icon: Badge.count(
              count: dateRangeCount,
              isLabelVisible: dateRangeCount > 0,
              child: const Icon(Icons.date_range_rounded),
            ),
          ),
          const SizedBox(width: AppSpacing.xs),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        heroTag: 'reminders-fab',
        onPressed: _openCreate,
        icon: const Icon(Icons.add_alarm_rounded),
        label: const Text('New reminder'),
      ),
      body: Column(
        children: [
          if (permissions.canViewAllStaff)
            Padding(
              padding: const EdgeInsets.fromLTRB(
                AppSpacing.lg,
                AppSpacing.sm,
                AppSpacing.lg,
                0,
              ),
              child: Align(
                alignment: Alignment.centerLeft,
                child: SegmentedButton<bool>(
                  showSelectedIcon: false,
                  segments: const [
                    ButtonSegment(value: false, label: Text('My reminders')),
                    ButtonSegment(value: true, label: Text('All staff')),
                  ],
                  selected: {filters.allStaff},
                  onSelectionChanged: (s) => _controller.applyFilters(
                    filters.copyWith(allStaff: s.first),
                  ),
                ),
              ),
            ),
          AppSearchField(
            hintText: 'Search title or description…',
            initialValue: filters.search,
            onChanged: _onSearchChanged,
          ),
          AppFilterChipBar(
            children: [
              AppFilterChip(
                label: 'All',
                selected: filters.status == null,
                onTap: () =>
                    _controller.applyFilters(filters.copyWith(status: null)),
              ),
              for (final s in ReminderStatus.values)
                AppFilterChip(
                  label: s.label,
                  selected: filters.status == s,
                  onTap: () =>
                      _controller.applyFilters(filters.copyWith(status: s)),
                ),
            ],
          ),
          const SizedBox(height: AppSpacing.xs),
          Expanded(
            child: AsyncValueView<ReminderListState>(
              value: asyncState,
              onRetry: _controller.refresh,
              isEmpty: (s) => s.items.isEmpty,
              empty: EmptyState(
                icon: Icons.alarm_outlined,
                title: filters.activeCount > 0 ||
                        (filters.search ?? '').isNotEmpty
                    ? 'No matching reminders'
                    : 'No reminders',
                message: filters.activeCount > 0 ||
                        (filters.search ?? '').isNotEmpty
                    ? 'Try clearing the search or filters.'
                    : "You're all caught up.",
              ),
              data: (s) => RefreshIndicator(
                onRefresh: _controller.refresh,
                child: ListView.separated(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(
                    AppSpacing.lg,
                    AppSpacing.sm,
                    AppSpacing.lg,
                    AppSpacing.fabClearance,
                  ),
                  itemCount: s.items.length,
                  separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.sm),
                  itemBuilder: (context, index) {
                    final reminder = s.items[index];
                    final highlighted = _highlightId != null &&
                        reminder.reminderId == _highlightId;
                    return Builder(
                      builder: (itemContext) {
                        if (highlighted &&
                            _scrolledIds.add(reminder.reminderId)) {
                          WidgetsBinding.instance.addPostFrameCallback((_) {
                            if (itemContext.mounted) {
                              Scrollable.ensureVisible(
                                itemContext,
                                alignment: 0.3,
                                duration: const Duration(milliseconds: 400),
                                curve: Curves.easeOut,
                              );
                            }
                          });
                        }
                        return _ReminderCard(
                          reminder: reminder,
                          highlighted: highlighted,
                          canEdit: permissions.canEdit(reminder),
                          onDismiss: () => _controller.dismiss(reminder),
                          onEdit: () => _openEdit(reminder),
                        );
                      },
                    );
                  },
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ReminderCard extends StatelessWidget {
  const _ReminderCard({
    required this.reminder,
    required this.onDismiss,
    required this.onEdit,
    required this.canEdit,
    this.highlighted = false,
  });

  final Reminder reminder;
  final VoidCallback onDismiss;
  final VoidCallback onEdit;
  final bool canEdit;
  final bool highlighted;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final route = _relatedRoute(reminder);

    return HighlightGlow(
      highlighted: highlighted,
      child: InkWell(
        borderRadius: BorderRadius.circular(AppRadii.lg),
        onTap: route == null ? null : () => context.push(route),
        child: SectionCard(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            reminder.title,
                            style: theme.textTheme.titleSmall?.copyWith(
                              fontWeight: FontWeight.w700,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(width: 8),
                        StatusChip(
                          tone: reminder.priority.tone,
                          rawStatus: reminder.priority.wire,
                          dense: true,
                        ),
                      ],
                    ),
                    if (reminder.description != null &&
                        reminder.description!.trim().isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(
                        reminder.description!,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        Icon(
                          Icons.schedule_rounded,
                          size: 14,
                          color: theme.colorScheme.outline,
                        ),
                        const SizedBox(width: 4),
                        Flexible(
                          child: Text(
                            Formatters.relative(reminder.remindAt),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Flexible(
                          child: StatusChip(
                            tone: reminder.status.tone,
                            rawStatus: reminder.status.wire,
                            dense: true,
                          ),
                        ),
                      ],
                    ),
                    if (reminder.assignedUserName != null) ...[
                      const SizedBox(height: 6),
                      Row(
                        children: [
                          Icon(
                            Icons.person_outline_rounded,
                            size: 14,
                            color: theme.colorScheme.outline,
                          ),
                          const SizedBox(width: 4),
                          Flexible(
                            child: Text(
                              reminder.assignedUserName!,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: theme.textTheme.labelSmall?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ],
                ),
              ),
              if (canEdit) ...[
                const SizedBox(width: 4),
                IconButton(
                  tooltip: 'Edit',
                  icon: const Icon(Icons.edit_outlined),
                  onPressed: onEdit,
                ),
              ],
              if (reminder.isActionable) ...[
                const SizedBox(width: 4),
                IconButton(
                  tooltip: 'Dismiss',
                  icon: const Icon(Icons.check_circle_outline_rounded),
                  onPressed: onDismiss,
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// Due-date range filter — separate from the status chip bar since it's a
/// two-value pick, matching the payment list's advanced-filter sheet pattern.
class _ReminderDateRangeSheet extends StatefulWidget {
  const _ReminderDateRangeSheet({required this.initial});

  final ReminderFilters initial;

  @override
  State<_ReminderDateRangeSheet> createState() =>
      _ReminderDateRangeSheetState();
}

class _ReminderDateRangeSheetState extends State<_ReminderDateRangeSheet> {
  late DateTime? _from = widget.initial.remindFrom;
  late DateTime? _to = widget.initial.remindTo;

  Future<void> _pickFrom() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _from ?? now,
      firstDate: DateTime(now.year - 2),
      lastDate: DateTime(now.year + 5),
    );
    if (picked != null) setState(() => _from = DateTime(picked.year, picked.month, picked.day));
  }

  Future<void> _pickTo() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _to ?? _from ?? now,
      firstDate: DateTime(now.year - 2),
      lastDate: DateTime(now.year + 5),
    );
    if (picked != null) {
      // End-of-day so the picked day itself is included in the range.
      setState(() => _to = DateTime(picked.year, picked.month, picked.day, 23, 59, 59));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.xl,
          0,
          AppSpacing.xl,
          AppSpacing.lg,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Due date range',
              style: theme.textTheme.titleSmall?.copyWith(
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: AppSpacing.md),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.event_rounded),
              title: const Text('From'),
              subtitle: Text(
                _from == null ? 'Any' : Formatters.date(_from),
              ),
              trailing: _from == null
                  ? null
                  : IconButton(
                      icon: const Icon(Icons.close_rounded),
                      onPressed: () => setState(() => _from = null),
                    ),
              onTap: _pickFrom,
            ),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.event_rounded),
              title: const Text('To'),
              subtitle: Text(_to == null ? 'Any' : Formatters.date(_to)),
              trailing: _to == null
                  ? null
                  : IconButton(
                      icon: const Icon(Icons.close_rounded),
                      onPressed: () => setState(() => _to = null),
                    ),
              onTap: _pickTo,
            ),
            const SizedBox(height: AppSpacing.xl),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => Navigator.of(context).pop(
                      widget.initial.copyWith(remindFrom: null, remindTo: null),
                    ),
                    child: const Text('Reset'),
                  ),
                ),
                const SizedBox(width: AppSpacing.md),
                Expanded(
                  child: FilledButton(
                    onPressed: () => Navigator.of(context).pop(
                      widget.initial.copyWith(remindFrom: _from, remindTo: _to),
                    ),
                    child: const Text('Apply'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
