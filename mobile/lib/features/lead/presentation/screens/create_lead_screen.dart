import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/routing/routes.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../data/lead_models.dart';
import '../../data/lead_repository.dart';
import '../../data/lead_suggestions.dart';
import '../providers/lead_providers.dart';

/// UC-24.2 — Create Quick Lead.
///
/// A name and one way to reach the person is the whole requirement; everything
/// else is folded under "More details". That mirrors what the server actually
/// enforces, and it is the difference between recording a walk-in while the
/// guest is still at the desk and asking them to wait through nine fields.
class CreateLeadScreen extends ConsumerStatefulWidget {
  const CreateLeadScreen({super.key});

  @override
  ConsumerState<CreateLeadScreen> createState() => _CreateLeadScreenState();
}

class _CreateLeadScreenState extends ConsumerState<CreateLeadScreen> {
  final _formKey = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _phone = TextEditingController();
  final _email = TextEditingController();
  final _company = TextEditingController();
  final _interestedService = TextEditingController();
  final _notes = TextEditingController();

  /// One of [kLeadSourceOptions], or null for "not specified". A value rather
  /// than a controller because the field is a closed list — see the dropdown.
  String? _source;

  bool _isCorporate = false;
  bool _submitting = false;
  bool _autovalidate = false;

  /// The optional half of the form starts folded away — see [_MoreDetails].
  bool _showDetails = false;

  /// How many optional fields carry something, so the collapsed header can say
  /// so without the user opening it.
  int get _optionalFilledCount => [
    _source ?? '',
    _interestedService.text,
    _company.text,
    _notes.text,
  ].where((v) => v.trim().isNotEmpty).length;

  /// BR-05: a lead cannot leave NEW without both of these.
  bool get _followUpFieldsMissing =>
      _source == null || _interestedService.text.trim().isEmpty;

  /// Switching to an individual clears the company, so a name typed under
  /// "Organization" and then hidden by the toggle is not quietly saved onto a
  /// lead whose form no longer shows it. Mirrors the web's type selector.
  void _setCorporate(bool value) {
    setState(() {
      _isCorporate = value;
      if (!value) _company.clear();
    });
  }

  /// BR: a lead needs a phone or an email. Cross-field, so it belongs to
  /// neither input's own validator — it is checked on submit and shown against
  /// the phone field, whose message names both ways of satisfying it.
  String? _reachabilityError;

  @override
  void dispose() {
    for (final c in [_name, _phone, _email, _company, _interestedService, _notes]) {
      c.dispose();
    }
    super.dispose();
  }

  /// Re-runs the phone-or-email rule as the user types, but only once it has
  /// already fired — nagging about a missing contact before the form has been
  /// submitted would flag every empty form.
  void _revalidateReachability() {
    if (_reachabilityError == null) return;
    final next = LeadFieldRules.validateReachable(
      email: _email.text,
      phone: _phone.text,
    );
    if (next != _reachabilityError) setState(() => _reachabilityError = next);
  }

  /// The first complaint from a field that lives inside "More details", or null.
  ///
  /// Those fields stay mounted while folded away — which is what makes them
  /// validate — but their error text is folded away with them. Without this, a
  /// corporate lead whose company was left blank refused to save and showed
  /// nothing at all: the button simply stopped responding.
  String? get _hiddenFieldError =>
      LeadFieldRules.validateCompanyName(
        _company.text,
        isCorporate: _isCorporate,
      ) ??
      LeadFieldRules.validateInterestedService(_interestedService.text);

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    final reachability = LeadFieldRules.validateReachable(
      email: _email.text,
      phone: _phone.text,
    );
    setState(() => _reachabilityError = reachability);
    if (!_formKey.currentState!.validate() || reachability != null) {
      // Only when the section is shut: with it open the field shows its own
      // error, and repeating it in a snackbar is noise.
      final hidden = _showDetails ? null : _hiddenFieldError;
      setState(() {
        _autovalidate = true;
        // Open the section so the error it holds is on screen — and say it out
        // loud too, because opening the section does not scroll to it.
        if (hidden != null) _showDetails = true;
      });
      if (hidden != null && mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(hidden)));
      }
      return;
    }
    setState(() => _submitting = true);
    final messenger = ScaffoldMessenger.of(context);
    final router = GoRouter.of(context);
    try {
      final lead = await ref
          .read(leadRepositoryProvider)
          .createLead(
            CreateLeadPayload(
              fullName: _name.text.trim(),
              // Sent in the one shape the column accepts, whatever the user typed.
              phone: LeadFieldRules.normalizePhone(_phone.text),
              email: _email.text.trim(),
              companyName: _company.text.trim(),
              source: _source,
              interestedService: _interestedService.text.trim(),
              notes: _notes.text.trim(),
              isCorporate: _isCorporate,
            ),
          );
      // refresh() (not invalidate) so the list reloads without dropping the
      // user's active search/filters. Only when the list is actually alive —
      // reading the notifier otherwise would spawn an unowned autoDispose
      // provider that dies mid-refresh (deep-link case).
      if (ref.exists(leadListControllerProvider)) {
        unawaited(ref.read(leadListControllerProvider.notifier).refresh());
      }
      messenger.showSnackBar(
        SnackBar(content: Text('Lead "${lead.fullName}" created')),
      );
      router.pop();
    } on ApiException catch (e) {
      if (mounted) setState(() => _submitting = false);
      // UC-8.1 duplicate detection. The contact details are checked against
      // customers as well as leads, and each answer sends the user somewhere
      // different: DUPLICATE_LEAD carries the existing lead's id, the two
      // DUPLICATE_CUSTOMER_* codes carry a customer id. Handling only the lead
      // case left the more useful of the two ("this person is already on the
      // books") as a dead-end snackbar.
      if (e.details != null && mounted) {
        final target = switch (e.errorCode) {
          'DUPLICATE_LEAD' => (
            title: 'Duplicate lead',
            action: 'View existing lead',
            route: RouteNames.leadDetail,
          ),
          'DUPLICATE_CUSTOMER_EMAIL' || 'DUPLICATE_CUSTOMER_PHONE' => (
            title: 'Already a customer',
            action: 'View customer',
            route: RouteNames.customerDetail,
          ),
          _ => null,
        };
        if (target != null) {
          await _showDuplicateDialog(
            message: e.message,
            existingId: e.details!,
            title: target.title,
            actionLabel: target.action,
            routeName: target.route,
          );
          return;
        }
      }
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    } on ValidationException catch (e) {
      if (mounted) setState(() => _submitting = false);
      // A 400 from bean validation carries "Validation failed for request." as
      // its message and the part that actually helps in `errors`. Showing only
      // the message told the user something was wrong and nothing about what.
      messenger.showSnackBar(SnackBar(content: Text(_describe(e))));
    } on AppException catch (e) {
      if (mounted) setState(() => _submitting = false);
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  /// The per-field messages if the server sent any, else its own message.
  static String _describe(ValidationException e) {
    if (e.fieldErrors.isEmpty) return e.message;
    return e.fieldErrors.values.join('\n');
  }

  Future<void> _showDuplicateDialog({
    required String message,
    required String existingId,
    required String title,
    required String actionLabel,
    required String routeName,
  }) async {
    final view = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.copy_all_rounded),
        title: Text(title),
        content: Text('$message\n\nYou can open the existing record instead.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Keep editing'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(actionLabel),
          ),
        ],
      ),
    );
    if (view == true && mounted) {
      // Swap this form for the existing record's detail screen.
      context.pushReplacementNamed(
        routeName,
        pathParameters: {'id': existingId},
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('New lead')),
      body: Form(
        key: _formKey,
        autovalidateMode: _autovalidate
            ? AutovalidateMode.onUserInteraction
            : AutovalidateMode.disabled,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, AppSpacing.huge),
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
            const SizedBox(height: 16),
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
            const SizedBox(height: 16),
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
            // Everything past this point is optional at creation. It is folded
            // away by default because a walk-in has to be recordable in the time
            // the guest is still standing there — the essentials above are all
            // the server needs.
            _MoreDetails(
              expanded: _showDetails,
              onToggle: _submitting
                  ? null
                  : () {
                      // Folding the section away while one of its fields has
                      // focus leaves the keyboard up over a field nobody can
                      // see, with the caret still in it.
                      if (_showDetails) FocusScope.of(context).unfocus();
                      setState(() => _showDetails = !_showDetails);
                    },
              // Named so the count is visible without expanding: a rep who filled
              // these in on a previous lead can see at a glance that this one is
              // still bare.
              filledCount: _optionalFilledCount,
              children: [
                // A closed list, matching the web's Source Channel select.
                // Enquiries arrive through a known set of channels, and letting
                // this be typed is what produced `Website Inquiry`, `WEBSITE`
                // and `Website` as three separate sources in the same column.
                //
                // "Not specified" is a real option rather than an implied
                // default: the web preselects `Website Inquiry`, which is why it
                // now accounts for 44% of every lead and why a genuine website
                // enquiry can no longer be told from a field nobody touched.
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
                    for (final option in kLeadSourceOptions)
                      DropdownMenuItem<String?>(
                        value: option,
                        child: Text(option),
                      ),
                  ],
                  onChanged: _submitting
                      ? null
                      : (value) => setState(() => _source = value),
                ),
                const SizedBox(height: AppSpacing.lg),
                _SuggestionField(
                  controller: _interestedService,
                  enabled: !_submitting,
                  label: 'Interested service',
                  hint: 'What are they asking about?',
                  icon: Icons.room_service_outlined,
                  suggestions:
                      ref
                          .watch(interestedServiceSuggestionsProvider)
                          .valueOrNull ??
                      kInterestedServiceFallback,
                  validator: LeadFieldRules.validateInterestedService,
                  onChanged: (_) => setState(() {}),
                ),
                const SizedBox(height: AppSpacing.sm),
                SwitchListTile(
                  value: _isCorporate,
                  onChanged: _submitting ? null : _setCorporate,
                  title: const Text('Organization'),
                  subtitle: const Text('A company rather than an individual'),
                  contentPadding: EdgeInsets.zero,
                ),
                // Only an organization has one, so the field appears with the
                // choice that makes it required rather than sitting there
                // unexplained — and unanswerable — on every individual lead.
                if (_isCorporate)
                  TextFormField(
                    controller: _company,
                    enabled: !_submitting,
                    // Keeps the collapsed header's filled-count honest.
                    onChanged: (_) => setState(() {}),
                    decoration: const InputDecoration(
                      labelText: 'Company / Organization *',
                      prefixIcon: Icon(Icons.business_outlined),
                    ),
                    // BR (mirrors backend): an organization lead must name its company.
                    validator: (v) =>
                        LeadFieldRules.validateCompanyName(v, isCorporate: true),
                  ),
                const SizedBox(height: AppSpacing.lg),
                TextFormField(
                  controller: _notes,
                  enabled: !_submitting,
                  // Keeps the collapsed header's filled-count honest.
                  onChanged: (_) => setState(() {}),
                  maxLines: 4,
                  decoration: const InputDecoration(
                    labelText: 'Notes',
                    alignLabelWithHint: true,
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.lg),
            // BR-05 stated before it can bite. Source and interested service are
            // optional here but required to leave New, so a lead created without
            // them is a lead whose next action will be refused — better said now,
            // while the form is open, than after the save has been celebrated.
            if (_followUpFieldsMissing)
              _NoteBanner(
                icon: Icons.info_outline_rounded,
                text: _showDetails
                    ? 'Source and interested service can wait, but this lead '
                          'stays New until both are filled in.'
                    : 'Saving now is fine. Add a source and an interested '
                          'service — under More details — before moving this '
                          'lead past New.',
              ),
            const SizedBox(height: AppSpacing.lg),
            FilledButton(
              onPressed: _submitting ? null : _submit,
              style: FilledButton.styleFrom(
                minimumSize: const Size.fromHeight(52),
              ),
              child: _submitting
                  ? const SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(strokeWidth: 2.5),
                    )
                  : const Text('Create lead'),
            ),
          ],
        ),
      ),
    );
  }
}

/// The optional half of the create form, folded away by default.
///
/// Quick capture is the point: a walk-in has to be recordable while the guest is
/// still standing there, and the server asks for a name and one way to reach the
/// person. Nine fields on one screen made that look like nine decisions. These
/// still matter — the header carries a count so a half-filled form cannot hide —
/// but they are not in the way of the save.
class _MoreDetails extends StatelessWidget {
  const _MoreDetails({
    required this.expanded,
    required this.onToggle,
    required this.filledCount,
    required this.children,
  });

  final bool expanded;
  final VoidCallback? onToggle;
  final int filledCount;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        InkWell(
          borderRadius: BorderRadius.circular(AppRadii.md),
          onTap: onToggle,
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm),
            child: Row(
              children: [
                Icon(
                  expanded
                      ? Icons.expand_less_rounded
                      : Icons.expand_more_rounded,
                  color: theme.colorScheme.primary,
                ),
                const SizedBox(width: AppSpacing.sm),
                // The label yields before the row does: at 320dp with a large
                // text scale, a fixed-width title plus the trailing hint
                // overflows, and an overflowing header is the first thing a
                // narrow phone shows.
                Expanded(
                  child: Row(
                    children: [
                      Flexible(
                        child: Text(
                          'More details',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.titleSmall?.copyWith(
                            fontWeight: FontWeight.w600,
                            color: theme.colorScheme.primary,
                          ),
                        ),
                      ),
                      if (filledCount > 0) ...[
                        const SizedBox(width: AppSpacing.md),
                        Badge(
                          label: Text('$filledCount'),
                          backgroundColor: theme.colorScheme.primary,
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(width: AppSpacing.sm),
                Text(
                  'Optional',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ),
        // Built either way so the fields stay registered with the Form and keep
        // their text while collapsed — an AnimatedCrossFade would rebuild them
        // and a validator on a collapsed field would never run.
        Offstage(
          offstage: !expanded,
          child: TickerMode(
            enabled: expanded,
            child: Padding(
              padding: const EdgeInsets.only(top: AppSpacing.sm),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: children,
              ),
            ),
          ),
        ),
      ],
    );
  }
}

/// A text field that offers known values without insisting on them.
///
/// The column behind it is free text on purpose — an enquiry can be for
/// something not in the catalogue yet — so this stays typable. What it changes
/// is the path of least resistance: tapping a chip is quicker than typing, and a
/// tapped chip always spells the value the same way. See [kLeadSourceOptions]
/// for what the free-text version did to the data.
class _SuggestionField extends StatelessWidget {
  const _SuggestionField({
    required this.controller,
    required this.enabled,
    required this.label,
    required this.hint,
    required this.icon,
    required this.suggestions,
    required this.validator,
    required this.onChanged,
  });

  final TextEditingController controller;
  final bool enabled;
  final String label;
  final String hint;
  final IconData icon;
  final List<String> suggestions;
  final FormFieldValidator<String> validator;
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
          decoration: InputDecoration(
            labelText: label,
            hintText: hint,
            prefixIcon: Icon(icon),
            suffixIcon: current.isEmpty
                ? null
                : IconButton(
                    tooltip: 'Clear',
                    icon: const Icon(Icons.close_rounded, size: 18),
                    onPressed: enabled
                        ? () {
                            controller.clear();
                            onChanged('');
                          }
                        : null,
                  ),
          ),
          validator: validator,
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
                // Re-tapping the selected chip clears it, so a mistap is undone
                // the same way it was made.
                onSelected: enabled
                    ? (_) {
                        final next = current == option ? '' : option;
                        // Cursor to the end, not to offset 0: the plain `text=`
                        // setter collapses the selection to the start, so the
                        // next character typed after tapping a chip would land
                        // in front of the value instead of after it.
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

/// A muted, tinted line of guidance — used for rules the user has not broken yet
/// but will.
class _NoteBanner extends StatelessWidget {
  const _NoteBanner({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = theme.colorScheme.tertiary;
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
