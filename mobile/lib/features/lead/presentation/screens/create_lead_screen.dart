import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_exception.dart';
import '../../data/lead_models.dart';
import '../../data/lead_repository.dart';
import '../providers/lead_providers.dart';

/// UC-24.2 — Create Quick Lead with realtime validation.
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
  final _source = TextEditingController();
  final _notes = TextEditingController();
  bool _isCorporate = false;
  bool _submitting = false;
  bool _autovalidate = false;

  @override
  void dispose() {
    for (final c in [_name, _phone, _email, _company, _source, _notes]) {
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

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate()) {
      setState(() => _autovalidate = true);
      return;
    }
    setState(() => _submitting = true);
    final messenger = ScaffoldMessenger.of(context);
    final router = GoRouter.of(context);
    try {
      final lead = await ref.read(leadRepositoryProvider).createLead(
            CreateLeadPayload(
              fullName: _name.text.trim(),
              phone: _phone.text.trim(),
              email: _email.text.trim(),
              companyName: _company.text.trim(),
              source: _source.text.trim(),
              notes: _notes.text.trim(),
              isCorporate: _isCorporate,
            ),
          );
      ref.invalidate(leadListControllerProvider);
      messenger.showSnackBar(
        SnackBar(content: Text('Lead "${lead.fullName}" created')),
      );
      router.pop();
    } on AppException catch (e) {
      if (mounted) setState(() => _submitting = false);
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('New lead')),
      body: Form(
        key: _formKey,
        autovalidateMode:
            _autovalidate ? AutovalidateMode.onUserInteraction : AutovalidateMode.disabled,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 40),
          children: [
            TextFormField(
              controller: _name,
              enabled: !_submitting,
              textCapitalization: TextCapitalization.words,
              decoration: const InputDecoration(
                labelText: 'Full name *',
                prefixIcon: Icon(Icons.person_outline),
              ),
              validator: (v) =>
                  (v == null || v.trim().isEmpty) ? 'Full name is required' : null,
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _phone,
              enabled: !_submitting,
              keyboardType: TextInputType.phone,
              decoration: const InputDecoration(
                labelText: 'Phone',
                prefixIcon: Icon(Icons.phone_outlined),
              ),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _email,
              enabled: !_submitting,
              keyboardType: TextInputType.emailAddress,
              decoration: const InputDecoration(
                labelText: 'Email',
                prefixIcon: Icon(Icons.mail_outline),
              ),
              validator: _validateEmail,
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _company,
              enabled: !_submitting,
              decoration: const InputDecoration(
                labelText: 'Company',
                prefixIcon: Icon(Icons.business_outlined),
              ),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _source,
              enabled: !_submitting,
              decoration: const InputDecoration(
                labelText: 'Source',
                prefixIcon: Icon(Icons.campaign_outlined),
                hintText: 'e.g. Referral, Website, Walk-in',
              ),
            ),
            const SizedBox(height: 8),
            SwitchListTile(
              value: _isCorporate,
              onChanged: _submitting ? null : (v) => setState(() => _isCorporate = v),
              title: const Text('Corporate lead'),
              subtitle: const Text('Organization rather than an individual'),
              contentPadding: EdgeInsets.zero,
            ),
            const SizedBox(height: 8),
            TextFormField(
              controller: _notes,
              enabled: !_submitting,
              maxLines: 4,
              decoration: const InputDecoration(
                labelText: 'Notes',
                alignLabelWithHint: true,
              ),
            ),
            const SizedBox(height: 24),
            FilledButton(
              onPressed: _submitting ? null : _submit,
              style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
              child: _submitting
                  ? const SizedBox(
                      width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.5))
                  : const Text('Create lead'),
            ),
          ],
        ),
      ),
    );
  }
}
