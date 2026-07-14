import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/detail_skeleton.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../../shared/widgets/status_chip.dart';
import '../../../auth/presentation/providers/auth_controller.dart';
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
        loading: const DetailSkeleton(),
        data: (lead) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(leadDetailProvider(leadId)),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, AppSpacing.xxxl),
            children: [
              _Header(lead: lead),
              const SizedBox(height: 16),
              SectionCard(
                title: 'Contact',
                icon: Icons.contact_page_outlined,
                child: Column(
                  children: [
                    InfoRow(
                      label: 'Phone',
                      value: lead.phone,
                      icon: Icons.phone_outlined,
                    ),
                    InfoRow(
                      label: 'Email',
                      value: lead.email,
                      icon: Icons.mail_outline,
                    ),
                    InfoRow(
                      label: 'Company',
                      value: lead.companyName,
                      icon: Icons.business_outlined,
                    ),
                    InfoRow(
                      label: 'Address',
                      value: lead.address,
                      icon: Icons.place_outlined,
                    ),
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
                    InfoRow(
                      label: 'Interested service',
                      value: lead.interestedService,
                    ),
                    InfoRow(label: 'Assigned to', value: lead.assignedUserName),
                    InfoRow(label: 'Created by', value: lead.createdByName),
                    InfoRow(
                      label: 'Created',
                      value: Formatters.dateTime(lead.createdAt),
                    ),
                    InfoRow(
                      label: 'Updated',
                      value: Formatters.dateTime(lead.updatedAt),
                    ),
                    if (lead.isConverted)
                      InfoRow(
                        label: 'Converted',
                        value: Formatters.dateTime(lead.convertedAt),
                      ),
                  ],
                ),
              ),
              if (lead.notes != null && lead.notes!.trim().isNotEmpty) ...[
                const SizedBox(height: 12),
                SectionCard(
                  title: 'Notes',
                  icon: Icons.sticky_note_2_outlined,
                  child: Text(
                    lead.notes!,
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ),
              ],
              const SizedBox(height: 20),
              if (!lead.status.isTerminal) ...[
                FilledButton.icon(
                  onPressed: () => _showStatusSheet(context, ref, lead),
                  icon: const Icon(Icons.swap_horiz_rounded),
                  label: const Text('Update status'),
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(50),
                  ),
                ),
                // UC-8.5: convert to a customer. Only assigned leads may convert
                // (backend rule); the sheet gates the manager-override path.
                if (lead.assignedUserId != null) ...[
                  const SizedBox(height: 12),
                  OutlinedButton.icon(
                    onPressed: () => _showConvertSheet(context, ref, lead),
                    icon: const Icon(Icons.how_to_reg_outlined),
                    label: const Text('Convert to customer'),
                    style: OutlinedButton.styleFrom(
                      minimumSize: const Size.fromHeight(50),
                    ),
                  ),
                ],
              ],
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _showStatusSheet(
    BuildContext context,
    WidgetRef ref,
    Lead lead,
  ) async {
    final selected = await showModalBottomSheet<LeadStatus>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(AppSpacing.xl, AppSpacing.xs, AppSpacing.xl, AppSpacing.md),
              child: Text(
                'Move lead to',
                style: Theme.of(context).textTheme.titleMedium,
              ),
            ),
            for (final s in lead.status.allowedTransitions)
              ListTile(
                onTap: () => Navigator.of(context).pop(s),
                leading: StatusChip(
                  tone: s.tone,
                  rawStatus: s.wire,
                  dense: true,
                ),
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
      await ref
          .read(leadRepositoryProvider)
          .updateStatus(lead.leadId, selected);
      ref.invalidate(leadDetailProvider(leadId));
      // refresh() (not invalidate) so the list reloads without dropping the
      // user's active search/filters. Only when the list is actually alive —
      // reading the notifier otherwise would spawn an unowned autoDispose
      // provider that dies mid-refresh (deep-link case).
      if (ref.exists(leadListControllerProvider)) {
        unawaited(ref.read(leadListControllerProvider.notifier).refresh());
      }
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            'Status updated to ${Formatters.humanizeEnum(selected.wire)}',
          ),
        ),
      );
    } on AppException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  Future<void> _showConvertSheet(
    BuildContext context,
    WidgetRef ref,
    Lead lead,
  ) async {
    // BR-07: only a Manager/Admin may override the "must be QUALIFIED" rule.
    final roles = ref.read(currentUserProvider)?.roles ?? const <String>[];
    final canOverride = roles.contains('MANAGER') || roles.contains('ADMIN');

    final converted = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _ConvertLeadSheet(lead: lead, canOverride: canOverride),
    );

    if (converted == true) {
      ref.invalidate(leadDetailProvider(leadId));
      if (ref.exists(leadListControllerProvider)) {
        unawaited(ref.read(leadListControllerProvider.notifier).refresh());
      }
    }
  }
}

/// UC-8.5 Convert-to-customer confirmation sheet. Customer details are inherited
/// from the lead; a Manager/Admin converting a not-yet-QUALIFIED lead must record
/// an approval reason (BR-07). Pops `true` on a successful conversion.
class _ConvertLeadSheet extends ConsumerStatefulWidget {
  const _ConvertLeadSheet({required this.lead, required this.canOverride});

  final Lead lead;
  final bool canOverride;

  @override
  ConsumerState<_ConvertLeadSheet> createState() => _ConvertLeadSheetState();
}

class _ConvertLeadSheetState extends ConsumerState<_ConvertLeadSheet> {
  final _reason = TextEditingController();
  bool _submitting = false;

  bool get _isQualified => widget.lead.status == LeadStatus.qualified;
  bool get _canConfirm =>
      _isQualified ||
      (widget.canOverride && _reason.text.trim().isNotEmpty);

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_canConfirm) return;
    setState(() => _submitting = true);
    final messenger = ScaffoldMessenger.of(context);
    final navigator = Navigator.of(context);
    final lead = widget.lead;
    try {
      await ref.read(leadRepositoryProvider).convertLead(
            lead.leadId,
            ConvertLeadPayload(
              customerType: lead.isCorporate ? 'CORPORATE' : 'INDIVIDUAL',
              fullName: lead.fullName,
              email: lead.email,
              phone: lead.phone,
              companyName: lead.companyName,
              address: lead.address,
              reason: _isQualified ? null : _reason.text.trim(),
            ),
          );
      messenger.showSnackBar(
        SnackBar(content: Text('${lead.fullName} converted to a customer')),
      );
      navigator.pop(true);
    } on AppException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final lead = widget.lead;

    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(
          AppSpacing.xl,
          AppSpacing.xs,
          AppSpacing.xl,
          AppSpacing.xl + MediaQuery.of(context).viewInsets.bottom,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Convert to customer', style: theme.textTheme.titleMedium),
            const SizedBox(height: AppSpacing.xs),
            Text(
              'Create a customer profile for ${lead.fullName} '
              '(${lead.isCorporate ? 'Organization' : 'Individual'}). '
              'The original lead is kept for history.',
              style: theme.textTheme.bodySmall?.copyWith(
                color: scheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            if (_isQualified)
              _EligibilityNote(
                tone: StatusTone.success,
                icon: Icons.verified_outlined,
                text: 'This lead is qualified — you can convert it now.',
              )
            else if (widget.canOverride) ...[
              _EligibilityNote(
                tone: StatusTone.warning,
                icon: Icons.gpp_maybe_outlined,
                text:
                    'This lead is ${Formatters.humanizeEnum(lead.status.wire)}, '
                    'not yet Qualified. As a manager you may approve an exception '
                    '— record the reason to enable conversion.',
              ),
              const SizedBox(height: AppSpacing.md),
              TextField(
                controller: _reason,
                enabled: !_submitting,
                minLines: 2,
                maxLines: 4,
                onChanged: (_) => setState(() {}),
                decoration: const InputDecoration(
                  labelText: 'Approval reason *',
                  hintText: 'e.g. Walk-in guest with a confirmed booking.',
                  border: OutlineInputBorder(),
                  alignLabelWithHint: true,
                ),
              ),
            ] else
              _EligibilityNote(
                tone: StatusTone.warning,
                icon: Icons.gpp_maybe_outlined,
                text:
                    'This lead is ${Formatters.humanizeEnum(lead.status.wire)}. '
                    'It must reach Qualified before conversion, or a Sales Manager '
                    'must approve an exception.',
              ),
            const SizedBox(height: AppSpacing.xl),
            FilledButton.icon(
              onPressed: (_submitting || !_canConfirm) ? null : _submit,
              icon: _submitting
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.how_to_reg_rounded),
              label: Text(_isQualified ? 'Confirm conversion' : 'Approve & convert'),
              style: FilledButton.styleFrom(
                minimumSize: const Size.fromHeight(50),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Small tinted eligibility banner used inside the convert sheet.
class _EligibilityNote extends StatelessWidget {
  const _EligibilityNote({
    required this.tone,
    required this.icon,
    required this.text,
  });

  final StatusTone tone;
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = tone == StatusTone.success
        ? theme.colorScheme.primary
        : theme.colorScheme.tertiary;
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.10),
        borderRadius: BorderRadius.circular(AppRadii.md),
        border: Border.all(color: color.withValues(alpha: 0.35)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: AppIconSize.sm, color: color),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: Text(text, style: theme.textTheme.bodySmall),
          ),
        ],
      ),
    );
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
              Text(
                lead.fullName,
                style: theme.textTheme.titleLarge?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 6),
              StatusChip(tone: lead.status.tone, rawStatus: lead.status.wire),
            ],
          ),
        ),
      ],
    );
  }
}
