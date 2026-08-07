import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../shared/formatters.dart';
import '../../../booking/data/booking_models.dart';
import '../../../booking/data/booking_repository.dart';
import '../../../deal/data/deal_repository.dart';
import '../../../lead/data/lead_models.dart';
import '../../../lead/data/lead_repository.dart';
import '../../../payment/data/payment_models.dart';
import '../../../payment/data/payment_repository.dart';
import '../../../quotation/data/quotation_repository.dart';
import '../../data/reminder_models.dart';

/// Searchable Lead / Deal / Quotation / Booking / Deposit picker for the
/// create-reminder form — the mobile equivalent of the web `CreateReminderModal`'s
/// "Linked To" + "Select Record" pair, collapsed into one sheet. Pops with a
/// [ReminderRelatedEntityLink].
///
/// [initialType], when set, opens the sheet already on that entity kind (e.g.
/// reached from a lead/deal/quotation detail screen) — mirrors
/// `TaskEntityPickerSheet`'s single-purpose sibling but keeps all five kinds
/// in one place since a reminder's `relatedEntity` is a fixed, required field.
class ReminderEntityPickerSheet extends ConsumerStatefulWidget {
  const ReminderEntityPickerSheet({super.key, this.initialType});

  final ReminderRelatedEntityType? initialType;

  @override
  ConsumerState<ReminderEntityPickerSheet> createState() =>
      _ReminderEntityPickerSheetState();
}

class _ReminderEntityPickerSheetState
    extends ConsumerState<ReminderEntityPickerSheet> {
  late ReminderRelatedEntityType _kind =
      widget.initialType ?? ReminderRelatedEntityType.lead;
  final _search = TextEditingController();
  Timer? _debounce;
  String _query = '';
  bool _loading = false;
  List<ReminderRelatedEntityLink> _results = const [];
  Object? _error;

  @override
  void dispose() {
    _debounce?.cancel();
    _search.dispose();
    super.dispose();
  }

  void _onChanged(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 320), () {
      _query = value.trim();
      _fetch();
    });
  }

  void _switchKind(ReminderRelatedEntityType kind) {
    if (_kind == kind) return;
    setState(() {
      _kind = kind;
      _results = const [];
      _error = null;
    });
    _fetch();
  }

  Future<void> _fetch() async {
    if (_query.isEmpty) {
      setState(() {
        _results = const [];
        _loading = false;
        _error = null;
      });
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    final query = _query;
    final kind = _kind;
    try {
      final List<ReminderRelatedEntityLink> results;
      switch (kind) {
        case ReminderRelatedEntityType.lead:
          final page = await ref
              .read(leadRepositoryProvider)
              .getLeads(filters: LeadFilters(search: query), size: 8);
          results = [
            for (final l in page.items)
              ReminderRelatedEntityLink(
                type: kind,
                id: l.leadId,
                label: l.fullName,
                subtitle: [
                  l.companyName,
                  l.phone,
                ].whereType<String>().where((s) => s.isNotEmpty).join(' · '),
              ),
          ];
        case ReminderRelatedEntityType.deal:
          final deals = await ref
              .read(dealRepositoryProvider)
              .getDeals(search: query);
          results = [
            for (final d in deals.take(8))
              ReminderRelatedEntityLink(
                type: kind,
                id: d.id,
                label: d.title,
                subtitle: d.displayStage,
              ),
          ];
        case ReminderRelatedEntityType.quotation:
          // No server-side search on `GET /quotations` — filter client-side
          // over the (owner-scoped) full list, same as the web fallback.
          final quotations = await ref
              .read(quotationRepositoryProvider)
              .getQuotations();
          final keyword = query.toLowerCase();
          results = [
            for (final q in quotations)
              if (q.quoteNo.toLowerCase().contains(keyword) ||
                  (q.contactName ?? '').toLowerCase().contains(keyword))
                ReminderRelatedEntityLink(
                  type: kind,
                  id: q.id,
                  label: q.quoteNo,
                  subtitle: q.contactName,
                ),
          ].take(8).toList();
        case ReminderRelatedEntityType.booking:
          final page = await ref
              .read(bookingRepositoryProvider)
              .getBookings(filters: BookingFilters(search: query), size: 8);
          results = [
            for (final b in page.items)
              ReminderRelatedEntityLink(
                type: kind,
                id: b.bookingId,
                label: b.bookingCode ?? b.bookingId,
                subtitle: b.customerName,
              ),
          ];
        case ReminderRelatedEntityType.deposit:
          final page = await ref
              .read(paymentRepositoryProvider)
              .getPayments(filters: PaymentFilters(search: query), size: 8);
          results = [
            for (final p in page.items)
              ReminderRelatedEntityLink(
                type: kind,
                id: p.paymentId,
                label: Formatters.money(p.amount),
                subtitle: [
                  p.bookingCode,
                  p.customerName,
                ].whereType<String>().where((s) => s.isNotEmpty).join(' · '),
              ),
          ];
      }
      // Ignore responses that arrive after the query/kind moved on.
      if (!mounted || query != _query || kind != _kind) return;
      setState(() {
        _results = results;
        _loading = false;
      });
    } catch (e) {
      if (!mounted || query != _query || kind != _kind) return;
      setState(() {
        _error = e;
        _loading = false;
      });
    }
  }

  String get _kindPlural => switch (_kind) {
    ReminderRelatedEntityType.lead => 'leads',
    ReminderRelatedEntityType.deal => 'deals',
    ReminderRelatedEntityType.quotation => 'quotations',
    ReminderRelatedEntityType.booking => 'bookings',
    ReminderRelatedEntityType.deposit => 'payments',
  };

  IconData get _kindIcon => switch (_kind) {
    ReminderRelatedEntityType.lead => Icons.person_search_rounded,
    ReminderRelatedEntityType.deal => Icons.handshake_rounded,
    ReminderRelatedEntityType.quotation => Icons.description_outlined,
    ReminderRelatedEntityType.booking => Icons.event_available_rounded,
    ReminderRelatedEntityType.deposit => Icons.payments_outlined,
  };

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.only(
          left: 16,
          right: 16,
          bottom: MediaQuery.of(context).viewInsets.bottom + 16,
        ),
        child: SizedBox(
          height: MediaQuery.of(context).size.height * 0.7,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Link to a record', style: theme.textTheme.titleMedium),
              const SizedBox(height: 4),
              Text(
                'Every reminder must be linked to a lead, deal, quotation, '
                'booking or deposit.',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: scheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 12),
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: SegmentedButton<ReminderRelatedEntityType>(
                  showSelectedIcon: false,
                  segments: [
                    for (final t in ReminderRelatedEntityType.values)
                      ButtonSegment(value: t, label: Text(t.label)),
                  ],
                  selected: {_kind},
                  onSelectionChanged: (s) => _switchKind(s.first),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _search,
                autofocus: true,
                onChanged: _onChanged,
                textInputAction: TextInputAction.search,
                decoration: InputDecoration(
                  hintText: 'Search $_kindPlural…',
                  prefixIcon: const Icon(Icons.search_rounded),
                  isDense: true,
                ),
              ),
              const SizedBox(height: 8),
              Expanded(child: _buildResults(theme, scheme)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildResults(ThemeData theme, ColorScheme scheme) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null) {
      return _CenteredNote(
        icon: Icons.error_outline_rounded,
        message: 'Search failed. Check your connection and try again.',
        action: TextButton(onPressed: _fetch, child: const Text('Retry')),
      );
    }
    if (_query.isEmpty) {
      return const _CenteredNote(
        icon: Icons.search_rounded,
        message: 'Start typing to search.',
      );
    }
    if (_results.isEmpty) {
      return _CenteredNote(
        icon: Icons.search_off_rounded,
        message: 'No $_kindPlural found for "$_query".',
      );
    }
    return ListView.builder(
      itemCount: _results.length,
      itemBuilder: (context, i) {
        final item = _results[i];
        return ListTile(
          leading: CircleAvatar(
            backgroundColor: scheme.primaryContainer,
            child: Icon(
              _kindIcon,
              size: 18,
              color: scheme.onPrimaryContainer,
            ),
          ),
          title: Text(item.label, maxLines: 1, overflow: TextOverflow.ellipsis),
          subtitle: (item.subtitle == null || item.subtitle!.isEmpty)
              ? null
              : Text(
                  item.subtitle!,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
          trailing: Text(
            item.type.wire,
            style: theme.textTheme.labelSmall?.copyWith(
              color: scheme.onSurfaceVariant,
              fontWeight: FontWeight.w700,
              letterSpacing: 0.8,
            ),
          ),
          onTap: () => Navigator.of(context).pop(item),
        );
      },
    );
  }
}

class _CenteredNote extends StatelessWidget {
  const _CenteredNote({required this.icon, required this.message, this.action});

  final IconData icon;
  final String message;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 32, color: theme.colorScheme.outline),
          const SizedBox(height: 8),
          Text(
            message,
            textAlign: TextAlign.center,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          ?action,
        ],
      ),
    );
  }
}
