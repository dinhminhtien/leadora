import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../data/lead_models.dart';
import '../../data/lead_repository.dart';
import '../providers/lead_providers.dart';

/// UC-24.3 View Lead Detail + UC-24.4 Update Lead Status.
class LeadDetailScreen extends ConsumerWidget {
  const LeadDetailScreen({super.key, required this.leadId});

  final String leadId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(leadDetailProvider(leadId));

    return Scaffold(
      appBar: AppBar(title: const Text('Lead detail')),
      body: AsyncValueView<Lead>(
        value: async,
        onRetry: () => ref.invalidate(leadDetailProvider(leadId)),
        data: (lead) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(leadDetailProvider(leadId)),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
            children: [
              _Header(lead: lead),
              const SizedBox(height: 16),
              SectionCard(
                title: 'Contact',
                icon: Icons.contact_page_outlined,
                child: Column(
                  children: [
                    InfoRow(label: 'Phone', value: lead.phone, icon: Icons.phone_outlined),
                    InfoRow(label: 'Email', value: lead.email, icon: Icons.mail_outline),
                    InfoRow(label: 'Company', value: lead.companyName, icon: Icons.business_outlined),
                    InfoRow(label: 'Address', value: lead.address, icon: Icons.place_outlined),
                    InfoRow(
                      label: 'Type',
                      value: lead.isCorporate ? 'Corporate' : 'Individual',
                      icon: Icons.category_outlined,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              SectionCard(
                title: 'Pipeline',
                icon: Icons.timeline_outlined,
                child: Column(
                  children: [
                    InfoRow(label: 'Source', value: lead.source),
                    InfoRow(label: 'Assigned to', value: lead.assignedUserName),
                    InfoRow(label: 'Created by', value: lead.createdByName),
                    InfoRow(label: 'Created', value: Formatters.dateTime(lead.createdAt)),
                    InfoRow(label: 'Updated', value: Formatters.dateTime(lead.updatedAt)),
                    if (lead.isConverted)
                      InfoRow(label: 'Converted', value: Formatters.dateTime(lead.convertedAt)),
                  ],
                ),
              ),
              if (lead.notes != null && lead.notes!.trim().isNotEmpty) ...[
                const SizedBox(height: 12),
                SectionCard(
                  title: 'Notes',
                  icon: Icons.sticky_note_2_outlined,
                  child: Text(lead.notes!, style: Theme.of(context).textTheme.bodyMedium),
                ),
              ],
              const SizedBox(height: 20),
              if (!lead.status.isTerminal)
                FilledButton.icon(
                  onPressed: () => _showStatusSheet(context, ref, lead),
                  icon: const Icon(Icons.swap_horiz_rounded),
                  label: const Text('Update status'),
                  style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(50)),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _showStatusSheet(BuildContext context, WidgetRef ref, Lead lead) async {
    final selected = await showModalBottomSheet<LeadStatus>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 4, 20, 12),
              child: Text('Move lead to',
                  style: Theme.of(context).textTheme.titleMedium),
            ),
            for (final s in lead.status.allowedTransitions)
              ListTile(
                onTap: () => Navigator.of(context).pop(s),
                leading: StatusChip(tone: s.tone, rawStatus: s.wire, dense: true),
                title: Text(Formatters.humanizeEnum(s.wire)),
                trailing: const Icon(Icons.arrow_forward_rounded, size: 18),
              ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );

    if (selected == null || selected == lead.status) return;
    if (!context.mounted) return;

    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(leadRepositoryProvider).updateStatus(lead.leadId, selected);
      ref.invalidate(leadDetailProvider(leadId));
      // refresh() (not invalidate) so the list reloads without dropping the
      // user's active search/filters. Only when the list is actually alive —
      // reading the notifier otherwise would spawn an unowned autoDispose
      // provider that dies mid-refresh (deep-link case).
      if (ref.exists(leadListControllerProvider)) {
        unawaited(ref.read(leadListControllerProvider.notifier).refresh());
      }
      messenger.showSnackBar(
        SnackBar(content: Text('Status updated to ${Formatters.humanizeEnum(selected.wire)}')),
      );
    } on AppException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.lead});

  final Lead lead;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      children: [
        AppAvatar(name: lead.fullName, radius: 30),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(lead.fullName,
                  style: theme.textTheme.titleLarge
                      ?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 6),
              StatusChip(tone: lead.status.tone, rawStatus: lead.status.wire),
            ],
          ),
        ),
      ],
    );
  }
}
