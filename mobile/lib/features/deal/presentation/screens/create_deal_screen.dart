import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../data/deal_models.dart';
import '../providers/deal_providers.dart';

/// Create a deal. Stage and status are intentionally not editable here — the
/// backend opens every new deal at PROSPECTING/OPEN, and the stage gates (deal
/// value, notes, close date) only make sense once the deal exists.
class CreateDealScreen extends ConsumerStatefulWidget {
  const CreateDealScreen({super.key});

  @override
  ConsumerState<CreateDealScreen> createState() => _CreateDealScreenState();
}

class _CreateDealScreenState extends ConsumerState<CreateDealScreen> {
  final _formKey = GlobalKey<FormState>();
  final _title = TextEditingController();
  final _contact = TextEditingController();
  final _email = TextEditingController();
  final _phone = TextEditingController();
  final _value = TextEditingController();
  final _notes = TextEditingController();

  DateTime? _expectedClose;
  bool _submitting = false;
  bool _autovalidate = false;

  @override
  void dispose() {
    for (final c in [_title, _contact, _email, _phone, _value, _notes]) {
      c.dispose();
    }
    super.dispose();
  }

  String? _validateEmail(String? v) {
    final email = v?.trim() ?? '';
    if (email.isEmpty) return null; // optional
    final ok = RegExp(r'^[\w.\-+]+@([\w\-]+\.)+[\w\-]{2,}$').hasMatch(email);
    return ok ? null : 'Enter a valid email';
  }

  String? _validateValue(String? v) {
    final raw = v?.trim() ?? '';
    if (raw.isEmpty) return null; // optional
    final parsed = double.tryParse(raw);
    if (parsed == null) return 'Enter a number';
    if (parsed < 0) return 'Value cannot be negative';
    return null;
  }

  Future<void> _pickCloseDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _expectedClose ?? now,
      firstDate: DateTime(now.year - 1),
      lastDate: DateTime(now.year + 5),
    );
    if (picked != null) setState(() => _expectedClose = picked);
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate()) {
      setState(() => _autovalidate = true);
      return;
    }
    setState(() => _submitting = true);
    final messenger = ScaffoldMessenger.of(context);
    final router = GoRouter.of(context);

    String? trimmedOrNull(TextEditingController c) {
      final text = c.text.trim();
      return text.isEmpty ? null : text;
    }

    try {
      final deal = await ref
          .read(dealActionsProvider)
          .create(
            DealPayload(
              title: _title.text.trim(),
              contactName: _contact.text.trim(),
              email: trimmedOrNull(_email),
              phone: trimmedOrNull(_phone),
              value: double.tryParse(_value.text.trim()),
              expectedClose: _expectedClose,
              notes: trimmedOrNull(_notes),
            ),
          );
      messenger.showSnackBar(
        SnackBar(content: Text('Deal "${deal.title}" created')),
      );
      router.pop();
    } on AppException catch (e) {
      if (mounted) setState(() => _submitting = false);
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('New deal')),
      body: SafeArea(
        child: Form(
          key: _formKey,
          autovalidateMode: _autovalidate
              ? AutovalidateMode.onUserInteraction
              : AutovalidateMode.disabled,
          child: ListView(
            padding: const EdgeInsets.all(AppSpacing.lg),
            children: [
              TextFormField(
                controller: _title,
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(
                  labelText: 'Deal name *',
                  prefixIcon: Icon(Icons.work_outline_rounded),
                ),
                validator: (v) => (v?.trim().isEmpty ?? true)
                    ? 'Deal name is required'
                    : null,
              ),
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _contact,
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(
                  labelText: 'Contact name *',
                  prefixIcon: Icon(Icons.person_outline_rounded),
                  helperText: 'Reuses an existing customer when the name or '
                      'email already exists',
                  helperMaxLines: 2,
                ),
                validator: (v) => (v?.trim().isEmpty ?? true)
                    ? 'Contact name is required'
                    : null,
              ),
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _email,
                keyboardType: TextInputType.emailAddress,
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(
                  labelText: 'Email',
                  prefixIcon: Icon(Icons.mail_outline_rounded),
                ),
                validator: _validateEmail,
              ),
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _phone,
                keyboardType: TextInputType.phone,
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(
                  labelText: 'Phone',
                  prefixIcon: Icon(Icons.phone_outlined),
                ),
              ),
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _value,
                keyboardType: const TextInputType.numberWithOptions(
                  decimal: true,
                ),
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(
                  labelText: 'Expected value',
                  prefixIcon: Icon(Icons.payments_outlined),
                ),
                validator: _validateValue,
              ),
              const SizedBox(height: AppSpacing.lg),
              InkWell(
                onTap: _pickCloseDate,
                borderRadius: BorderRadius.circular(AppRadii.sm),
                child: InputDecorator(
                  decoration: const InputDecoration(
                    labelText: 'Estimated close date',
                    prefixIcon: Icon(Icons.event_outlined),
                  ),
                  child: Text(
                    _expectedClose == null
                        ? 'Not set'
                        : Formatters.date(_expectedClose),
                    style: _expectedClose == null
                        ? theme.textTheme.bodyLarge?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          )
                        : theme.textTheme.bodyLarge,
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _notes,
                maxLines: 4,
                decoration: const InputDecoration(
                  labelText: 'Notes',
                  alignLabelWithHint: true,
                ),
              ),
              const SizedBox(height: AppSpacing.xxl),
              FilledButton.icon(
                onPressed: _submitting ? null : _submit,
                icon: _submitting
                    ? const SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.check_rounded),
                label: Text(_submitting ? 'Creating…' : 'Create deal'),
              ),
              const SizedBox(height: AppSpacing.lg),
            ],
          ),
        ),
      ),
    );
  }
}
