import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/widgets/async_value_view.dart';
import '../../../../shared/widgets/detail_skeleton.dart';
import '../../data/lead_models.dart';
import '../../data/lead_repository.dart';
import '../../data/lead_suggestions.dart';
import '../providers/lead_providers.dart';

/// UC-8.4 — edit a lead's details.
///
/// **Why this screen had to exist.** Mobile could change a lead's status and
/// nothing else, and BR-05 refuses to move a lead out of NEW until it carries a
/// source and an interested service. A rep who created a lead in the quick form
/// therefore reached a lead they could neither advance nor complete: the server
/// told them what was missing and the app offered no way to supply it.
///
/// Every field the form shows is sent on save, blanks included — see
/// [UpdateLeadPayload]. Status and assignee are not editable here: advancing a
/// lead is the status flow's job, and assigning it is a manager's.
class EditLeadScreen extends ConsumerWidget {
  const EditLeadScreen({super.key, required this.leadId});

  final String leadId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(leadDetailProvider(leadId));

    return Scaffold(
      appBar: AppBar(title: const Text('Edit lead')),
      body: AsyncValueView<Lead>(
        value: async,
        onRetry: () => ref.invalidate(leadDetailProvider(leadId)),
        loading: const DetailSkeleton(),
        data: (lead) => _EditLeadForm(lead: lead),
      ),
    );
  }
}

class _EditLeadForm extends ConsumerStatefulWidget {
  const _EditLeadForm({required this.lead});

  final Lead lead;

  @override
  ConsumerState<_EditLeadForm> createState() => _EditLeadFormState();
}

class _EditLeadFormState extends ConsumerState<_EditLeadForm> {
  final _formKey = GlobalKey<FormState>();

  late final _name = TextEditingController(text: widget.lead.fullName);
  late final _phone = TextEditingController(text: widget.lead.phone ?? '');
  late final _email = TextEditingController(text: widget.lead.email ?? '');
  late final _company = TextEditingController(
    text: widget.lead.companyName ?? '',
  );
  late final _interestedService = TextEditingController(
    text: widget.lead.interestedService ?? '',
  );
  late final _address = TextEditingController(text: widget.lead.address ?? '');
  late final _notes = TextEditingController(text: widget.lead.notes ?? '');

  /// A stored source outside [kLeadSourceOptions] — rows predating the closed
  /// list hold `WEBSITE`, `SOCIAL` and the like — is offered as its own option
  /// rather than silently rewritten to null when the form opens. Editing a
  /// phone number must not quietly erase a lead's source.
  late String? _source = widget.lead.source?.trim().isEmpty ?? true
      ? null
      : widget.lead.source;

  late bool _isCorporate = widget.lead.isCorporate;

  bool _submitting = false;
  bool _autovalidate = false;
  String? _reachabilityError;

  @override
  void dispose() {
    for (final c in [
      _name,
      _phone,
      _email,
      _company,
      _interestedService,
      _address,
      _notes,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  /// The stored value first when it is not one of the standard channels, so a
  /// legacy source stays selectable instead of vanishing on the first save.
  List<String> get _sourceOptions {
    final stored = _source;
    if (stored == null || kLeadSourceOptions.contains(stored)) {
      return kLeadSourceOptions;
    }
    return [stored, ...kLeadSourceOptions];
  }

  void _revalidateReachability() {
    if (_reachabilityError == null) return;
    final next = LeadFieldRules.validateReachable(
      email: _email.text,
      phone: _phone.text,
    );
    if (next != _reachabilityError) setState(() => _reachabilityError = next);
  }

  void _setCorporate(bool value) {
    setState(() {
      _isCorporate = value;
      if (!value) _company.clear();
    });
  }

  UpdateLeadPayload get _payload => UpdateLeadPayload(
    fullName: _name.text,
    // Stored in the one shape the column accepts, whatever the user typed.
    phone: LeadFieldRules.normalizePhone(_phone.text),
    email: _email.text,
    companyName: _company.text,
    address: _address.text,
    isCorporate: _isCorporate,
    source: _source,
    interestedService: _interestedService.text,
    notes: _notes.text,
  );

  Future<void> _save() async {
    FocusScope.of(context).unfocus();
    final reachability = LeadFieldRules.validateReachable(
      email: _email.text,
      phone: _phone.text,
    );
    setState(() => _reachabilityError = reachability);
    if (!_formKey.currentState!.validate() || reachability != null) {
      setState(() => _autovalidate = true);
      return;
    }

    setState(() => _submitting = true);
    final messenger = ScaffoldMessenger.of(context);
    final router = GoRouter.of(context);
    try {
      await ref
          .read(leadRepositoryProvider)
          .updateLead(widget.lead.leadId, _payload);
      ref.invalidate(leadDetailProvider(widget.lead.leadId));
      // refresh() rather than invalidate so the list reloads without dropping
      // the user's active search/filters, and only while it is actually alive.
      if (ref.exists(leadListControllerProvider)) {
        unawaited(ref.read(leadListControllerProvider.notifier).refresh());
      }
      messenger.showSnackBar(const SnackBar(content: Text('Lead updated')));
      router.pop();
    } on ValidationException catch (e) {
      if (mounted) setState(() => _submitting = false);
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            e.fieldErrors.isEmpty
                ? e.message
                : e.fieldErrors.values.join('\n'),
          ),
        ),
      );
    } on AppException catch (e) {
      if (mounted) setState(() => _submitting = false);
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    // What this edit would leave BR-05 still wanting — recomputed as the user
    // types, so the hint clears the moment the field it names is filled.
    final gate = LeadStatusGate.of(_payload.applyTo(widget.lead));

    return Form(
      key: _formKey,
      autovalidateMode: _autovalidate
          ? AutovalidateMode.onUserInteraction
          : AutovalidateMode.disabled,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.lg,
          AppSpacing.lg,
          AppSpacing.lg,
          AppSpacing.huge,
        ),
        children: [
          TextFormField(
            controller: _name,
            enabled: !_submitting,
            textCapitalization: TextCapitalization.words,
            decoration: const InputDecoration(
              labelText: 'Full name *',
              prefixIcon: Icon(Icons.person_outline),
            ),
            validator: LeadFieldRules.validateFullName,
          ),
          const SizedBox(height: AppSpacing.lg),
          TextFormField(
            controller: _phone,
            enabled: !_submitting,
            keyboardType: TextInputType.phone,
            onChanged: (_) => _revalidateReachability(),
            decoration: InputDecoration(
              labelText: 'Phone',
              prefixIcon: const Icon(Icons.phone_outlined),
              hintText: '0912345678',
              errorText: _reachabilityError,
            ),
            validator: LeadFieldRules.validatePhone,
          ),
          const SizedBox(height: AppSpacing.lg),
          TextFormField(
            controller: _email,
            enabled: !_submitting,
            keyboardType: TextInputType.emailAddress,
            onChanged: (_) => _revalidateReachability(),
            decoration: const InputDecoration(
              labelText: 'Email',
              prefixIcon: Icon(Icons.mail_outline),
            ),
            validator: LeadFieldRules.validateEmail,
          ),
          const SizedBox(height: AppSpacing.lg),
          DropdownButtonFormField<String?>(
            initialValue: _source,
            isExpanded: true,
            decoration: const InputDecoration(
              labelText: 'Source',
              prefixIcon: Icon(Icons.campaign_outlined),
            ),
            items: [
              const DropdownMenuItem<String?>(
                value: null,
                child: Text('Not specified'),
              ),
              for (final option in _sourceOptions)
                DropdownMenuItem<String?>(value: option, child: Text(option)),
            ],
            onChanged: _submitting
                ? null
                : (value) => setState(() => _source = value),
          ),
          const SizedBox(height: AppSpacing.lg),
          _ServiceField(
            controller: _interestedService,
            enabled: !_submitting,
            suggestions:
                ref.watch(interestedServiceSuggestionsProvider).valueOrNull ??
                kInterestedServiceFallback,
            onChanged: (_) => setState(() {}),
          ),
          const SizedBox(height: AppSpacing.md),
          // The reason the rep came here, kept in front of them and clearing as
          // they type — a hint that only appears after a failed save teaches
          // nothing.
          if (gate.missing.isNotEmpty)
            _Callout(
              tone: _CalloutTone.warning,
              icon: Icons.pending_actions_outlined,
              text:
                  'Still needed before this lead can leave New: '
                  '${LeadStatusGate.of(_payload.applyTo(widget.lead)).missing.join(' and ')}.',
            )
          else
            const _Callout(
              tone: _CalloutTone.success,
              icon: Icons.check_circle_outline,
              text: 'This lead has what it needs to move to Contacted.',
            ),
          const SizedBox(height: AppSpacing.md),
          SwitchListTile(
            value: _isCorporate,
            onChanged: _submitting ? null : _setCorporate,
            title: const Text('Organization'),
            subtitle: const Text('A company rather than an individual'),
            contentPadding: EdgeInsets.zero,
          ),
          if (_isCorporate) ...[
            TextFormField(
              controller: _company,
              enabled: !_submitting,
              decoration: const InputDecoration(
                labelText: 'Company / Organization *',
                prefixIcon: Icon(Icons.business_outlined),
              ),
              validator: (v) =>
                  LeadFieldRules.validateCompanyName(v, isCorporate: true),
            ),
            const SizedBox(height: AppSpacing.lg),
          ],
          TextFormField(
            controller: _address,
            enabled: !_submitting,
            decoration: const InputDecoration(
              labelText: 'Address',
              prefixIcon: Icon(Icons.place_outlined),
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          TextFormField(
            controller: _notes,
            enabled: !_submitting,
            maxLines: 4,
            decoration: const InputDecoration(
              labelText: 'Notes',
              alignLabelWithHint: true,
            ),
          ),
          const SizedBox(height: AppSpacing.xl),
          FilledButton(
            onPressed: _submitting ? null : _save,
            style: FilledButton.styleFrom(
              minimumSize: const Size.fromHeight(52),
            ),
            child: _submitting
                ? const SizedBox(
                    width: 22,
                    height: 22,
                    child: CircularProgressIndicator(strokeWidth: 2.5),
                  )
                : const Text('Save changes'),
          ),
        ],
      ),
    );
  }
}

/// Interested service, typable but with the hotel's catalogue one tap away.
/// Free text by design — an enquiry can be for something not in the catalogue —
/// while the chips keep one service from being spelled four ways.
class _ServiceField extends StatelessWidget {
  const _ServiceField({
    required this.controller,
    required this.enabled,
    required this.suggestions,
    required this.onChanged,
  });

  final TextEditingController controller;
  final bool enabled;
  final List<String> suggestions;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    final current = controller.text.trim();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        TextFormField(
          controller: controller,
          enabled: enabled,
          onChanged: onChanged,
          decoration: const InputDecoration(
            labelText: 'Interested service',
            hintText: 'What are they asking about?',
            prefixIcon: Icon(Icons.room_service_outlined),
          ),
          validator: LeadFieldRules.validateInterestedService,
        ),
        const SizedBox(height: AppSpacing.sm),
        Wrap(
          spacing: AppSpacing.sm,
          runSpacing: 0,
          children: [
            for (final option in suggestions)
              ChoiceChip(
                label: Text(option),
                selected: current == option,
                onSelected: enabled
                    ? (_) {
                        final next = current == option ? '' : option;
                        controller.value = TextEditingValue(
                          text: next,
                          selection: TextSelection.collapsed(
                            offset: next.length,
                          ),
                        );
                        onChanged(next);
                      }
                    : null,
              ),
          ],
        ),
      ],
    );
  }
}

enum _CalloutTone { success, warning }

/// A tinted line of guidance about a rule the user has not broken yet.
class _Callout extends StatelessWidget {
  const _Callout({
    required this.tone,
    required this.icon,
    required this.text,
  });

  final _CalloutTone tone;
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = tone == _CalloutTone.success
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
          Expanded(child: Text(text, style: theme.textTheme.bodySmall)),
        ],
      ),
    );
  }
}
