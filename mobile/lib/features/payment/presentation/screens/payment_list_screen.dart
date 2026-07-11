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
import '../../../../shared/widgets/list_skeleton.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/payment_models.dart';
import '../providers/payment_providers.dart';

/// Dummy row for the loading skeleton — same widget as a real row so the list
/// keeps its shape when data lands.
const _skeletonPayment = Payment(
  paymentId: '',
  amount: 12000000,
  bookingCode: 'BK-0000-0000',
  customerName: 'Placeholder customer name',
  status: PaymentStatus.pending,
  paymentType: PaymentType.deposit,
);

/// UC-21.3 — Payment list with server-side search, status chips, advanced
/// filters (payment type), sort, pull-to-refresh and infinite scroll.
class PaymentListScreen extends ConsumerStatefulWidget {
  const PaymentListScreen({super.key});

  @override
  ConsumerState<PaymentListScreen> createState() => _PaymentListScreenState();
}

class _PaymentListScreenState extends ConsumerState<PaymentListScreen> {
  final _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  PaymentListController get _controller =>
      ref.read(paymentListControllerProvider.notifier);

  void _onScroll() {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 400) {
      _controller.loadMore();
    }
  }

  void _onSearchChanged(String term) =>
      _controller.applyFilters(_controller.filters.copyWith(search: term));

  Future<void> _openSortSheet() async {
    final current = _controller.filters.sort;
    final picked = await showModalBottomSheet<PaymentSort>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: RadioGroup<PaymentSort>(
          groupValue: current,
          onChanged: (value) => Navigator.of(sheetContext).pop(value),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (final sort in PaymentSort.values)
                RadioListTile<PaymentSort>(
                  value: sort,
                  title: Text(sort.label),
                ),
              const SizedBox(height: AppSpacing.sm),
            ],
          ),
        ),
      ),
    );
    if (picked != null && mounted) {
      _controller.applyFilters(_controller.filters.copyWith(sort: picked));
    }
  }

  Future<void> _openFilterSheet() async {
    final result = await showModalBottomSheet<PaymentFilters>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _PaymentFilterSheet(initial: _controller.filters),
    );
    if (result != null) _controller.applyFilters(result);
  }

  @override
  Widget build(BuildContext context) {
    final asyncState = ref.watch(paymentListControllerProvider);
    final filters = asyncState.valueOrNull?.filters ?? const PaymentFilters();
    final typeCount = filters.paymentType != null ? 1 : 0;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Payments'),
        actions: [
          IconButton(
            tooltip: 'Sort',
            onPressed: _openSortSheet,
            icon: const Icon(Icons.swap_vert_rounded),
          ),
          IconButton(
            tooltip: 'Filters',
            onPressed: _openFilterSheet,
            icon: Badge.count(
              count: typeCount,
              isLabelVisible: typeCount > 0,
              child: const Icon(Icons.tune_rounded),
            ),
          ),
          const SizedBox(width: AppSpacing.xs),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        heroTag: 'payments-fab',
        onPressed: () => context.pushNamed(RouteNames.paymentCreate),
        icon: const Icon(Icons.request_quote_outlined),
        label: const Text('New request'),
      ),
      body: Column(
        children: [
          AppSearchField(
            hintText: 'Search booking code, customer…',
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
              for (final s in PaymentStatus.values)
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
            child: AsyncValueView<PaymentListState>(
              value: asyncState,
              onRetry: _controller.refresh,
              loading: ListSkeleton(
                separatorHeight: AppSpacing.sm,
                itemBuilder: (_) => const _PaymentCard(payment: _skeletonPayment),
              ),
              isEmpty: (s) => s.items.isEmpty,
              empty: EmptyState(
                icon: Icons.payments_outlined,
                title: filters.activeCount > 0 || (filters.search ?? '').isNotEmpty
                    ? 'No matching payments'
                    : 'No payments yet',
                message: filters.activeCount > 0 || (filters.search ?? '').isNotEmpty
                    ? 'Try clearing the search or filters.'
                    : 'Generate a payment request against a booking.',
              ),
              data: (s) => RefreshIndicator(
                onRefresh: _controller.refresh,
                child: ListView.separated(
                  controller: _scrollController,
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(
                    AppSpacing.lg,
                    AppSpacing.xs,
                    AppSpacing.lg,
                    AppSpacing.fabClearance,
                  ),
                  itemCount: s.items.length + (s.hasMore ? 1 : 0),
                  separatorBuilder: (_, _) =>
                      const SizedBox(height: AppSpacing.sm),
                  itemBuilder: (context, index) {
                    if (index >= s.items.length) {
                      return const Padding(
                        padding: EdgeInsets.all(AppSpacing.lg),
                        child: Center(child: CircularProgressIndicator()),
                      );
                    }
                    return _PaymentCard(payment: s.items[index]);
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

class _PaymentCard extends StatelessWidget {
  const _PaymentCard({required this.payment});

  final Payment payment;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;

    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => context.pushNamed(
          RouteNames.paymentDetail,
          pathParameters: {'id': payment.paymentId},
        ),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Text(
                      Formatters.money(payment.amount),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  const SizedBox(width: AppSpacing.sm),
                  StatusChip(
                    tone: payment.statusTone,
                    label: payment.displayStatus,
                    dense: true,
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.xs),
              Text(
                payment.customerName ?? 'Unknown customer',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: scheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: AppSpacing.md),
              Row(
                children: [
                  if (payment.paymentType != null) ...[
                    Icon(
                      Icons.sell_outlined,
                      size: AppIconSize.xs,
                      color: scheme.onSurfaceVariant,
                    ),
                    const SizedBox(width: AppSpacing.xs),
                    Text(
                      payment.paymentType!.label,
                      style: theme.textTheme.labelMedium?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                  const Spacer(),
                  if (payment.dueDate != null) ...[
                    Icon(
                      Icons.event_rounded,
                      size: AppIconSize.xs,
                      color: payment.isOverdue
                          ? scheme.error
                          : scheme.onSurfaceVariant,
                    ),
                    const SizedBox(width: AppSpacing.xs),
                    Text(
                      Formatters.shortDate(payment.dueDate),
                      style: theme.textTheme.labelMedium?.copyWith(
                        color: payment.isOverdue
                            ? scheme.error
                            : scheme.onSurfaceVariant,
                        fontWeight: payment.isOverdue
                            ? FontWeight.w700
                            : FontWeight.w500,
                      ),
                    ),
                  ],
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Advanced filters. Status lives in the chip bar; only payment type is here.
class _PaymentFilterSheet extends StatefulWidget {
  const _PaymentFilterSheet({required this.initial});

  final PaymentFilters initial;

  @override
  State<_PaymentFilterSheet> createState() => _PaymentFilterSheetState();
}

class _PaymentFilterSheetState extends State<_PaymentFilterSheet> {
  late PaymentFilters _draft = widget.initial;

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
              'Payment type',
              style: theme.textTheme.titleSmall?.copyWith(
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: AppSpacing.md),
            Wrap(
              spacing: AppSpacing.sm,
              children: [
                ChoiceChip(
                  label: const Text('Any'),
                  selected: _draft.paymentType == null,
                  onSelected: (_) =>
                      setState(() => _draft = _draft.copyWith(paymentType: null)),
                ),
                for (final t in PaymentType.values)
                  ChoiceChip(
                    label: Text(t.label),
                    selected: _draft.paymentType == t,
                    onSelected: (_) =>
                        setState(() => _draft = _draft.copyWith(paymentType: t)),
                  ),
              ],
            ),
            const SizedBox(height: AppSpacing.xxl),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => Navigator.of(
                      context,
                    ).pop(widget.initial.copyWith(paymentType: null)),
                    child: const Text('Reset'),
                  ),
                ),
                const SizedBox(width: AppSpacing.md),
                Expanded(
                  child: FilledButton(
                    onPressed: () => Navigator.of(context).pop(_draft),
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
