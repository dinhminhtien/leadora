import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../shared/formatters.dart';
import '../../../customer/data/customer_repository.dart';
import '../../../lead/data/lead_models.dart';
import '../../../lead/data/lead_repository.dart';

/// The lead **or** customer a task is linked to. Mirrors the web
/// `EntitySearchPicker` selection — exactly one of [leadId] / [customerId]
/// is set.
class TaskEntityLink {
  const TaskEntityLink({
    this.leadId,
    this.customerId,
    required this.name,
    this.phone,
    this.email,
    this.companyName,
  }) : assert(
         (leadId != null) ^ (customerId != null),
         'Link exactly one of lead / customer',
       );

  final String? leadId;
  final String? customerId;
  final String name;
  final String? phone;
  final String? email;
  final String? companyName;

  bool get isLead => leadId != null;

  String get detail =>
      [email, phone, companyName].whereType<String>().join(' · ');
}

/// Searchable Lead / Customer picker used by the task form — the mobile
/// equivalent of the web `EntitySearchPicker` (same debounce, same page size,
/// same lead-vs-customer tab toggle). Pops with a [TaskEntityLink].
class TaskEntityPickerSheet extends ConsumerStatefulWidget {
  const TaskEntityPickerSheet({super.key});

  @override
  ConsumerState<TaskEntityPickerSheet> createState() =>
      _TaskEntityPickerSheetState();
}

class _TaskEntityPickerSheetState extends ConsumerState<TaskEntityPickerSheet> {
  bool _customerTab = false;
  final _search = TextEditingController();
  Timer? _debounce;
  String _query = '';
  bool _loading = false;
  List<TaskEntityLink> _results = const [];
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

  void _switchTab(bool customer) {
    if (_customerTab == customer) return;
    setState(() {
      _customerTab = customer;
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
    final customerTab = _customerTab;
    try {
      final List<TaskEntityLink> results;
      if (customerTab) {
        final page = await ref
            .read(customerRepositoryProvider)
            .getCustomers(search: query, size: 8);
        results = [
          for (final c in page.items)
            TaskEntityLink(
              customerId: c.customerId,
              name: c.fullName,
              phone: c.phone,
              email: c.email,
              companyName: c.companyName,
            ),
        ];
      } else {
        final page = await ref
            .read(leadRepositoryProvider)
            .getLeads(filters: LeadFilters(search: query), size: 8);
        results = [
          for (final l in page.items)
            TaskEntityLink(
              leadId: l.leadId,
              name: l.fullName,
              phone: l.phone,
              email: l.email,
              companyName: l.companyName,
            ),
        ];
      }
      // Ignore responses that arrive after the query/tab moved on.
      if (!mounted || query != _query || customerTab != _customerTab) return;
      setState(() {
        _results = results;
        _loading = false;
      });
    } catch (e) {
      if (!mounted || query != _query || customerTab != _customerTab) return;
      setState(() {
        _error = e;
        _loading = false;
      });
    }
  }

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
          height: MediaQuery.of(context).size.height * 0.65,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Link to entity', style: theme.textTheme.titleMedium),
              const SizedBox(height: 4),
              Text(
                'Search by name, email, phone or company.',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: scheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 12),
              SegmentedButton<bool>(
                showSelectedIcon: false,
                segments: const [
                  ButtonSegment(
                    value: false,
                    label: Text('Lead'),
                    icon: Icon(Icons.person_search_rounded, size: 18),
                  ),
                  ButtonSegment(
                    value: true,
                    label: Text('Customer'),
                    icon: Icon(Icons.contacts_rounded, size: 18),
                  ),
                ],
                selected: {_customerTab},
                onSelectionChanged: (s) => _switchTab(s.first),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _search,
                autofocus: true,
                onChanged: _onChanged,
                textInputAction: TextInputAction.search,
                decoration: InputDecoration(
                  hintText: _customerTab
                      ? 'Search customers…'
                      : 'Search leads…',
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
        icon: Icons.person_off_outlined,
        message:
            'No ${_customerTab ? 'customers' : 'leads'} found for "$_query".',
      );
    }
    return ListView.builder(
      itemCount: _results.length,
      itemBuilder: (context, i) {
        final item = _results[i];
        return ListTile(
          leading: CircleAvatar(
            backgroundColor: item.isLead
                ? scheme.primaryContainer
                : scheme.tertiaryContainer,
            child: Text(
              Formatters.initials(item.name),
              style: theme.textTheme.labelMedium?.copyWith(
                fontWeight: FontWeight.w700,
                color: item.isLead
                    ? scheme.onPrimaryContainer
                    : scheme.onTertiaryContainer,
              ),
            ),
          ),
          title: Text(item.name, maxLines: 1, overflow: TextOverflow.ellipsis),
          subtitle: item.detail.isEmpty
              ? null
              : Text(item.detail, maxLines: 1, overflow: TextOverflow.ellipsis),
          trailing: Text(
            item.isLead ? 'LEAD' : 'CUSTOMER',
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
