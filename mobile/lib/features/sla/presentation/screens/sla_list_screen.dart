import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/highlight_glow.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/sla_models.dart';
import '../providers/sla_providers.dart';

const _highlightDuration = Duration(seconds: 4);

/// Maps an SLA entry's `entityType`/`entityId` to a screen this app can show.
/// BOOKING has no mobile screen yet — tapping those entries does nothing.
String? _relatedRoute(SlaTrackingEntry e) {
  if (e.entityId.isEmpty) return null;
  switch (e.entityType.toUpperCase()) {
    case 'LEAD':
      return Routes.leadDetailPath(e.entityId);
    case 'TASK':
      return Routes.taskDetailPath(e.entityId);
    case 'QUOTATION':
      return Routes.quotationDetailPath(e.entityId);
    default:
      return null;
  }
}

/// UC-17.3 — View SLA on Mobile. Read-only monitoring list; rule
/// configuration and the performance report stay web-only.
///
/// [highlightId], when set (arrives via the notification tap → `?highlight=`
/// deep-link, see `Routes.slaPath`), flashes and scrolls to the matching
/// tracking row once the list loads.
class SlaListScreen extends ConsumerStatefulWidget {
  const SlaListScreen({super.key, this.highlightId});

  final String? highlightId;

  @override
  ConsumerState<SlaListScreen> createState() => _SlaListScreenState();
}

class _SlaListScreenState extends ConsumerState<SlaListScreen> {
  SlaDisplayStatus? _filter;
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

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(slaMonitoringProvider(_filter?.wire));

    return Scaffold(
      appBar: AppBar(title: const Text('SLA monitoring')),
      body: Column(
        children: [
          SizedBox(
            height: 44,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: ChoiceChip(
                    label: const Text('All'),
                    selected: _filter == null,
                    onSelected: (_) => setState(() => _filter = null),
                  ),
                ),
                for (final s in SlaDisplayStatus.values)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    child: ChoiceChip(
                      label: Text(Formatters.humanizeEnum(s.wire)),
                      selected: _filter == s,
                      onSelected: (_) => setState(() => _filter = s),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 4),
          Expanded(
            child: AsyncValueView<List<SlaTrackingEntry>>(
              value: async,
              onRetry: () => ref.invalidate(slaMonitoringProvider(_filter?.wire)),
              isEmpty: (items) => items.isEmpty,
              empty: const EmptyState(
                icon: Icons.verified_outlined,
                title: 'Nothing to show',
                message: 'No SLA trackers match this filter.',
              ),
              data: (items) => RefreshIndicator(
                onRefresh: () async => ref.invalidate(slaMonitoringProvider(_filter?.wire)),
                child: ListView.separated(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 32),
                  itemCount: items.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 10),
                  itemBuilder: (context, index) {
                    final entry = items[index];
                    final highlighted = _highlightId != null && entry.trackingId == _highlightId;
                    return Builder(
                      builder: (itemContext) {
                        if (highlighted && _scrolledIds.add(entry.trackingId)) {
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
                        return _SlaCard(entry: entry, highlighted: highlighted);
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

class _SlaCard extends StatelessWidget {
  const _SlaCard({required this.entry, this.highlighted = false});

  final SlaTrackingEntry entry;
  final bool highlighted;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final route = _relatedRoute(entry);
    final hours = entry.hoursRemaining;
    final hoursLabel = entry.isResolved
        ? 'Resolved'
        : hours < 0
            ? '${-hours}h overdue'
            : '${hours}h left';

    return HighlightGlow(
      highlighted: highlighted,
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: route == null ? null : () => context.push(route),
        child: SectionCard(
          padding: const EdgeInsets.all(14),
          child: Row(
            children: [
              Icon(entry.icon, size: 22, color: theme.colorScheme.primary),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '${Formatters.humanizeEnum(entry.activityType)} · ${Formatters.humanizeEnum(entry.entityType)}',
                      style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'Deadline ${Formatters.dateTime(entry.deadlineAt)}',
                      style: theme.textTheme.bodySmall
                          ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  StatusChip(
                    tone: entry.displayStatus.tone,
                    rawStatus: entry.displayStatus.wire,
                    dense: true,
                  ),
                  const SizedBox(height: 6),
                  Text(
                    hoursLabel,
                    style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.outline),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
