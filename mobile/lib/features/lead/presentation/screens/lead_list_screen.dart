import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/lead_models.dart';
import '../providers/lead_providers.dart';

/// UC-24.14 / UC-24.15 — assigned lead list with search, status filter,
/// pull-to-refresh and infinite scroll.
class LeadListScreen extends ConsumerStatefulWidget {
  const LeadListScreen({super.key});

  @override
  ConsumerState<LeadListScreen> createState() => _LeadListScreenState();
}

class _LeadListScreenState extends ConsumerState<LeadListScreen> {
  final _scrollController = ScrollController();
  final _searchController = TextEditingController();
  Timer? _debounce;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _scrollController.dispose();
    _searchController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 400) {
      ref.read(leadListControllerProvider.notifier).loadMore();
    }
  }

  void _onSearchChanged(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 400), () {
      ref.read(leadListControllerProvider.notifier).applyFilters(search: value);
    });
  }

  @override
  Widget build(BuildContext context) {
    final asyncState = ref.watch(leadListControllerProvider);
    final controller = ref.read(leadListControllerProvider.notifier);
    final activeStatus = asyncState.valueOrNull?.status;

    return Scaffold(
      appBar: AppBar(title: const Text('Leads')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.pushNamed(RouteNames.leadCreate),
        icon: const Icon(Icons.person_add_alt_1_rounded),
        label: const Text('New lead'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
            child: TextField(
              controller: _searchController,
              onChanged: _onSearchChanged,
              textInputAction: TextInputAction.search,
              decoration: InputDecoration(
                hintText: 'Search name, phone, company…',
                prefixIcon: const Icon(Icons.search_rounded),
                isDense: true,
                suffixIcon: _searchController.text.isEmpty
                    ? null
                    : IconButton(
                        icon: const Icon(Icons.close_rounded),
                        onPressed: () {
                          _searchController.clear();
                          controller.applyFilters(search: '');
                        },
                      ),
              ),
            ),
          ),
          SizedBox(
            height: 44,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              children: [
                _StatusFilterChip(
                  label: 'All',
                  selected: activeStatus == null,
                  onTap: () => controller.applyFilters(clearStatus: true),
                ),
                for (final s in LeadStatus.values)
                  _StatusFilterChip(
                    label: Formatters.humanizeEnum(s.wire),
                    selected: activeStatus == s,
                    onTap: () => controller.applyFilters(status: s),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 4),
          Expanded(
            child: AsyncValueView<LeadListState>(
              value: asyncState,
              onRetry: controller.refresh,
              isEmpty: (s) => s.items.isEmpty,
              empty: const EmptyState(
                icon: Icons.people_outline_rounded,
                title: 'No leads found',
                message: 'Try clearing filters or create a new lead.',
              ),
              data: (s) => RefreshIndicator(
                onRefresh: controller.refresh,
                child: ListView.separated(
                  controller: _scrollController,
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 96),
                  itemCount: s.items.length + (s.hasMore ? 1 : 0),
                  separatorBuilder: (_, _) => const SizedBox(height: 10),
                  itemBuilder: (context, index) {
                    if (index >= s.items.length) {
                      return const Padding(
                        padding: EdgeInsets.all(16),
                        child: Center(child: CircularProgressIndicator()),
                      );
                    }
                    return _LeadCard(lead: s.items[index]);
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

class _StatusFilterChip extends StatelessWidget {
  const _StatusFilterChip({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: ChoiceChip(
        label: Text(label),
        selected: selected,
        onSelected: (_) => onTap(),
      ),
    );
  }
}

class _LeadCard extends StatelessWidget {
  const _LeadCard({required this.lead});

  final Lead lead;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return InkWell(
      borderRadius: BorderRadius.circular(16),
      onTap: () => context.pushNamed(
        RouteNames.leadDetail,
        pathParameters: {'id': lead.leadId},
      ),
      child: SectionCard(
        padding: const EdgeInsets.all(14),
        child: Row(
          children: [
            AppAvatar(name: lead.fullName, radius: 22),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    lead.fullName,
                    style: theme.textTheme.titleSmall
                        ?.copyWith(fontWeight: FontWeight.w700),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    [
                      if (lead.companyName != null && lead.companyName!.isNotEmpty)
                        lead.companyName,
                      lead.phone ?? lead.email ?? 'No contact',
                    ].whereType<String>().join(' · '),
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                StatusChip(tone: lead.status.tone, rawStatus: lead.status.wire, dense: true),
                const SizedBox(height: 6),
                Text(
                  Formatters.relative(lead.createdAt),
                  style: theme.textTheme.labelSmall
                      ?.copyWith(color: theme.colorScheme.outline),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
