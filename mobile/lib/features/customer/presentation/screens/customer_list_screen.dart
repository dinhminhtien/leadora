import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/empty_state.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/customer_models.dart';
import '../providers/customer_providers.dart';

/// Customer Profiles — search, type/status filters, live stat cards,
/// pull-to-refresh and infinite scroll. Mirrors the web Customer Profiles list.
class CustomerListScreen extends ConsumerStatefulWidget {
  const CustomerListScreen({super.key});

  @override
  ConsumerState<CustomerListScreen> createState() => _CustomerListScreenState();
}

class _CustomerListScreenState extends ConsumerState<CustomerListScreen> {
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

  CustomerListController get _controller =>
      ref.read(customerListControllerProvider.notifier);

  void _onScroll() {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 400) {
      _controller.loadMore();
    }
  }

  void _onSearchChanged(String value) {
    setState(() {});
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 400), () {
      _controller.applyFilters(_controller.filters.copyWith(search: value));
    });
  }

  void _clearSearch() {
    _debounce?.cancel();
    setState(() => _searchController.clear());
    _controller.applyFilters(_controller.filters.copyWith(search: ''));
  }

  Future<void> _openFilterSheet() async {
    final result = await showModalBottomSheet<CustomerFilters>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _CustomerFilterSheet(initial: _controller.filters),
    );
    if (result != null) _controller.applyFilters(result);
  }

  @override
  Widget build(BuildContext context) {
    final asyncState = ref.watch(customerListControllerProvider);
    final filters = asyncState.valueOrNull?.filters ?? const CustomerFilters();
    final activeCount =
        (filters.customerType != null ? 1 : 0) + (filters.status != null ? 1 : 0);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Customers'),
        actions: [
          IconButton(
            tooltip: 'Filters',
            onPressed: _openFilterSheet,
            icon: Badge.count(
              count: activeCount,
              isLabelVisible: activeCount > 0,
              child: const Icon(Icons.tune_rounded),
            ),
          ),
          const SizedBox(width: 4),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        // Pushed over the tab shell (whose leads FAB uses the default hero tag),
        // so give this one its own tag to avoid a Hero collision on transition.
        heroTag: 'customers-fab',
        onPressed: () => context.pushNamed(RouteNames.customerCreate),
        icon: const Icon(Icons.person_add_alt_1_rounded),
        label: const Text('New customer'),
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
                hintText: 'Search name, email, phone, company…',
                prefixIcon: const Icon(Icons.search_rounded),
                isDense: true,
                suffixIcon: _searchController.text.isEmpty
                    ? null
                    : IconButton(
                        icon: const Icon(Icons.close_rounded),
                        onPressed: _clearSearch,
                      ),
              ),
            ),
          ),
          const _CustomerStatsRow(),
          const SizedBox(height: 4),
          Expanded(
            child: AsyncValueView<CustomerListState>(
              value: asyncState,
              onRetry: _controller.refresh,
              isEmpty: (s) => s.items.isEmpty,
              empty: EmptyState(
                icon: Icons.people_outline_rounded,
                title: 'No customers found',
                message: 'Try clearing filters or add a new customer.',
                actionLabel: 'New customer',
                onAction: () => context.pushNamed(RouteNames.customerCreate),
              ),
              data: (s) => RefreshIndicator(
                onRefresh: _controller.refresh,
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
                    return _CustomerCard(customer: s.items[index]);
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

/// Horizontally scrollable global counts, unaffected by the active filters.
class _CustomerStatsRow extends ConsumerWidget {
  const _CustomerStatsRow();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(customerStatsProvider);
    final stats = async.valueOrNull;
    if (stats == null) return const SizedBox(height: 4);
    return SizedBox(
      height: 64,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        children: [
          _StatPill(label: 'Total', value: stats.total, tone: StatusTone.brand),
          _StatPill(label: 'Active', value: stats.active, tone: StatusTone.success),
          _StatPill(label: 'Individual', value: stats.individual, tone: StatusTone.info),
          _StatPill(label: 'Corporate', value: stats.corporate, tone: StatusTone.neutral),
        ],
      ),
    );
  }
}

class _StatPill extends StatelessWidget {
  const _StatPill({required this.label, required this.value, required this.tone});

  final String label;
  final int value;
  final StatusTone tone;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      margin: const EdgeInsets.only(right: AppSpacing.sm),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(AppRadii.md),
        border: Border.all(
            color: theme.colorScheme.outlineVariant.withValues(alpha: 0.6)),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(Formatters.compact(value),
              style: theme.textTheme.titleMedium
                  ?.copyWith(fontWeight: FontWeight.w800)),
          Text(label.toUpperCase(),
              style: theme.textTheme.labelSmall?.copyWith(
                letterSpacing: 0.4,
                color: theme.colorScheme.onSurfaceVariant,
              )),
        ],
      ),
    );
  }
}

class _CustomerFilterSheet extends StatefulWidget {
  const _CustomerFilterSheet({required this.initial});

  final CustomerFilters initial;

  @override
  State<_CustomerFilterSheet> createState() => _CustomerFilterSheetState();
}

class _CustomerFilterSheetState extends State<_CustomerFilterSheet> {
  late CustomerFilters _draft = widget.initial;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Filter customers', style: theme.textTheme.titleMedium),
            const SizedBox(height: 16),
            Text('Type', style: theme.textTheme.labelLarge),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: [
                ChoiceChip(
                  label: const Text('All'),
                  selected: _draft.customerType == null,
                  onSelected: (_) =>
                      setState(() => _draft = _draft.withType(null)),
                ),
                for (final t in CustomerType.values)
                  ChoiceChip(
                    label: Text(t.label),
                    selected: _draft.customerType == t,
                    onSelected: (_) =>
                        setState(() => _draft = _draft.withType(t)),
                  ),
              ],
            ),
            const SizedBox(height: 16),
            Text('Status', style: theme.textTheme.labelLarge),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: [
                ChoiceChip(
                  label: const Text('All'),
                  selected: _draft.status == null,
                  onSelected: (_) =>
                      setState(() => _draft = _draft.withStatus(null)),
                ),
                for (final s in CustomerStatus.values)
                  ChoiceChip(
                    label: Text(Formatters.humanizeEnum(s.wire)),
                    selected: _draft.status == s,
                    onSelected: (_) =>
                        setState(() => _draft = _draft.withStatus(s)),
                  ),
              ],
            ),
            const SizedBox(height: 20),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => Navigator.of(context).pop(
                        CustomerFilters(search: _draft.search)),
                    child: const Text('Reset'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  flex: 2,
                  child: FilledButton(
                    onPressed: () => Navigator.of(context).pop(_draft),
                    child: const Text('Apply filters'),
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

class _CustomerCard extends StatelessWidget {
  const _CustomerCard({required this.customer});

  final Customer customer;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final subtitle = [
      if (customer.companyName != null && customer.companyName!.isNotEmpty)
        customer.companyName,
      customer.phone ?? customer.email ?? 'No contact',
    ].whereType<String>().join(' · ');

    return InkWell(
      borderRadius: BorderRadius.circular(16),
      onTap: () => context.pushNamed(
        RouteNames.customerDetail,
        pathParameters: {'id': customer.customerId},
        extra: customer,
      ),
      child: SectionCard(
        padding: const EdgeInsets.all(14),
        child: Row(
          children: [
            AppAvatar(name: customer.fullName, radius: 22),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Flexible(
                        child: Text(
                          customer.fullName,
                          style: theme.textTheme.titleSmall
                              ?.copyWith(fontWeight: FontWeight.w700),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const SizedBox(width: 6),
                      StatusChip(
                        tone: customer.customerType.tone,
                        label: customer.customerType.label,
                        dense: true,
                      ),
                    ],
                  ),
                  const SizedBox(height: 2),
                  Text(
                    subtitle,
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            StatusChip(
              tone: customer.status.tone,
              rawStatus: customer.status.wire,
              dense: true,
            ),
          ],
        ),
      ),
    );
  }
}
