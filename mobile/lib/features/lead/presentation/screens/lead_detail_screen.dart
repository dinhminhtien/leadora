import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/routing/routes.dart';
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

    final lead = async.valueOrNull;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Lead detail'),
        actions: [
          // BR-08: a converted lead is a locked historical record, and a lost
          // one is closed — the server refuses every edit to either, so the
          // action is absent rather than offered and then denied.
          if (lead != null && !lead.status.isTerminal)
            IconButton(
              tooltip: 'Edit lead',
              onPressed: () => context.pushNamed(
                RouteNames.leadEdit,
                pathParameters: {'id': leadId},
              ),
              icon: const Icon(Icons.edit_outlined),
            ),
          const SizedBox(width: 4),
        ],
      ),
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
                // BR-04/BR-05/BR-06, checked here rather than discovered from a
                // 422. Both refusals disable the control and say what to do:
                // an unassigned lead is a draft nobody may move, and a lead
                // without a source and an interested service cannot enter
                // active follow-up.
                Builder(
                  builder: (context) {
                    final gate = LeadStatusGate.of(lead);
                    return Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        FilledButton.icon(
                          onPressed: gate.allBlocked
                              ? null
                              : () => _showStatusSheet(context, ref, lead),
                          icon: const Icon(Icons.swap_horiz_rounded),
                          label: const Text('Update status'),
                          style: FilledButton.styleFrom(
                            minimumSize: const Size.fromHeight(50),
                          ),
                        ),
                        if (gate.reason != null)
                          Padding(
                            padding: const EdgeInsets.only(top: AppSpacing.sm),
                            child: _HintText(gate.reason!),
                          ),
                        // The way out of the BR-05 refusal, next to the refusal
                        // itself — the fields it names are all editable.
                        if (!gate.unassigned && gate.missing.isNotEmpty)
                          Padding(
                            padding: const EdgeInsets.only(top: AppSpacing.sm),
                            child: OutlinedButton.icon(
                              onPressed: () => context.pushNamed(
                                RouteNames.leadEdit,
                                pathParameters: {'id': lead.leadId},
                              ),
                              icon: const Icon(Icons.edit_outlined),
                              label: const Text('Add the missing details'),
                            ),
                          ),
                      ],
                    );
                  },
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
            // BR-05 blocks a forward move but never Lost: a junk lead has to be
            // closable immediately rather than after filling in details nobody
            // will use. So the blocked option is shown greyed with its reason,
            // not hidden — hiding it would look like the ladder had changed.
            for (final s in lead.status.allowedTransitions)
              Builder(
                builder: (context) {
                  final gate = LeadStatusGate.of(lead);
                  final blocked =
                      s != LeadStatus.lost && gate.missing.isNotEmpty;
                  return ListTile(
                    enabled: !blocked,
                    onTap: blocked ? null : () => Navigator.of(context).pop(s),
                    leading: StatusChip(
                      tone: s.tone,
                      rawStatus: s.wire,
                      dense: true,
                    ),
                    title: Text(Formatters.humanizeEnum(s.wire)),
                    subtitle: blocked
                        ? Text('Needs ${gate.missing.join(' and ')} first')
                        : null,
                    trailing: blocked
                        ? const Icon(Icons.lock_outline_rounded, size: 18)
                        : const Icon(Icons.arrow_forward_rounded, size: 18),
                  );
                },
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
    final canOverride = ref.read(currentUserProvider)?.hasFullAccess ?? false;

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

/// UC-8.5 E6 — the customer this lead would have created already exists.
/// [customerId] comes from the refusal's `details`; [field] is the detail that
/// collided, so the note can say which one.
typedef _DuplicateCustomer = ({String customerId, String field});

class _ConvertLeadSheetState extends ConsumerState<_ConvertLeadSheet> {
  final _reason = TextEditingController();
  bool _submitting = false;

  /// Set when the server refuses the conversion because the person is already on
  /// the books. Turns the refusal into the choice UC-8.5 describes — link the
  /// lead to that profile, or cancel — instead of a snackbar with nowhere to go.
  _DuplicateCustomer? _duplicate;

  bool get _isQualified => widget.lead.status == LeadStatus.qualified;
  bool get _canConfirm =>
      _isQualified ||
      (widget.canOverride && _reason.text.trim().isNotEmpty);

  /// The approval reason travels with the link too: attaching a not-yet-QUALIFIED
  /// lead to a customer is the same exception BR-07 governs, taken by a different
  /// route.
  String? get _approvalReason => _isQualified ? null : _reason.text.trim();

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  /// Reads a duplicate-customer refusal out of a failed conversion, or null for
  /// every other error — which is what keeps the "link instead" button from
  /// appearing next to failures it cannot fix. A 409 carrying no id is still
  /// shown as a plain error rather than a button that would post nothing.
  static _DuplicateCustomer? _duplicateFrom(AppException e) {
    if (e is! ApiException || e.details == null) return null;
    return switch (e.errorCode) {
      'DUPLICATE_CUSTOMER_EMAIL' => (
        customerId: e.details!,
        field: 'email address',
      ),
      'DUPLICATE_CUSTOMER_PHONE' => (
        customerId: e.details!,
        field: 'phone number',
      ),
      _ => null,
    };
  }

  Future<void> _submit() async {
    if (!_canConfirm) return;
    await _run(
      () => ref.read(leadRepositoryProvider).convertLead(
            widget.lead.leadId,
            ConvertLeadPayload(
              customerType: widget.lead.isCorporate ? 'CORPORATE' : 'INDIVIDUAL',
              reason: _approvalReason,
            ),
          ),
      success: '${widget.lead.fullName} converted to a customer',
    );
  }

  /// UC-8.5 E6 — attach the lead to the customer that already exists rather than
  /// creating a second record for the same person. Both routes end the same way,
  /// so the caller cannot tell them apart.
  Future<void> _linkExisting() async {
    final duplicate = _duplicate;
    if (duplicate == null) return;
    await _run(
      () => ref.read(leadRepositoryProvider).linkLeadToCustomer(
            widget.lead.leadId,
            LinkLeadToCustomerPayload(
              customerId: duplicate.customerId,
              reason: _approvalReason,
            ),
          ),
      success: '${widget.lead.fullName} linked to the existing customer',
    );
  }

  /// Shared submit plumbing: spinner, the duplicate branch, and popping `true`.
  Future<void> _run(
    Future<String> Function() action, {
    required String success,
  }) async {
    setState(() => _submitting = true);
    final messenger = ScaffoldMessenger.of(context);
    final navigator = Navigator.of(context);
    try {
      await action();
      messenger.showSnackBar(SnackBar(content: Text(success)));
      navigator.pop(true);
    } on AppException catch (e) {
      final duplicate = _duplicateFrom(e);
      if (duplicate != null && mounted) {
        setState(() => _duplicate = duplicate);
      } else {
        messenger.showSnackBar(SnackBar(content: Text(e.message)));
      }
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
            // UC-8.5 E6. Once the server has told us this person is already a
            // customer, converting again can only fail the same way — so the
            // primary action becomes linking, and "Convert" steps aside.
            if (_duplicate != null) ...[
              const SizedBox(height: AppSpacing.lg),
              _EligibilityNote(
                tone: StatusTone.warning,
                icon: Icons.people_alt_outlined,
                text:
                    'A customer with this ${_duplicate!.field} already exists. '
                    'Link ${lead.fullName} to that profile instead of creating a '
                    'second record for the same person.',
              ),
              const SizedBox(height: AppSpacing.xl),
              FilledButton.icon(
                onPressed: _submitting ? null : _linkExisting,
                icon: _submitting
                    ? const _ButtonSpinner()
                    : const Icon(Icons.link_rounded),
                label: const Text('Link to existing customer'),
                style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(50),
                ),
              ),
              const SizedBox(height: AppSpacing.sm),
              TextButton(
                onPressed: _submitting
                    ? null
                    : () => Navigator.of(context).pop(false),
                child: const Text('Cancel'),
              ),
            ] else ...[
              const SizedBox(height: AppSpacing.xl),
              FilledButton.icon(
                onPressed: (_submitting || !_canConfirm) ? null : _submit,
                icon: _submitting
                    ? const _ButtonSpinner()
                    : const Icon(Icons.how_to_reg_rounded),
                label: Text(
                  _isQualified ? 'Confirm conversion' : 'Approve & convert',
                ),
                style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(50),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// The in-button progress indicator, sized to sit where the icon was so the
/// button does not resize when it starts working.
class _ButtonSpinner extends StatelessWidget {
  const _ButtonSpinner();

  @override
  Widget build(BuildContext context) => const SizedBox(
    width: 18,
    height: 18,
    child: CircularProgressIndicator(strokeWidth: 2),
  );
}

/// Muted explanatory line under a disabled action — says why it is disabled,
/// which a greyed-out button on its own never does.
class _HintText extends StatelessWidget {
  const _HintText(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(
          Icons.info_outline_rounded,
          size: AppIconSize.sm,
          color: theme.colorScheme.onSurfaceVariant,
        ),
        const SizedBox(width: AppSpacing.sm),
        Expanded(
          child: Text(
            text,
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
      ],
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
