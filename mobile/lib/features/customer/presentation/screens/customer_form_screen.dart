import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../task/presentation/screens/task_list_screen.dart'
    show TaskAssigneePicker;
import '../../../user/data/user_models.dart';
import '../../data/customer_models.dart';
import '../../data/customer_repository.dart';
import '../providers/customer_providers.dart';

enum CustomerFormMode { create, edit }

/// Resolves the source customer for edit. Prefers the [initial] handed over via
/// go_router `extra` (instant); refetches by [customerId] if it was dropped
/// (e.g. process death) so the form still opens.
class CustomerFormLoader extends ConsumerWidget {
  const CustomerFormLoader({super.key, required this.customerId, this.initial});

  final String customerId;
  final Customer? initial;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (initial != null) {
      return CustomerFormScreen(mode: CustomerFormMode.edit, customer: initial);
    }
    final async = ref.watch(customerDetailProvider(customerId));
    return async.when(
      data: (c) => CustomerFormScreen(mode: CustomerFormMode.edit, customer: c),
      loading: () =>
          const Scaffold(body: Center(child: CircularProgressIndicator())),
      error: (error, _) => Scaffold(
        appBar: AppBar(),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.xxl),
            child: Text(
              error is AppException
                  ? error.message
                  : 'Could not load the customer.',
              textAlign: TextAlign.center,
            ),
          ),
        ),
      ),
    );
  }
}

/// UC create/update customer profile — one form for both. Field layout mirrors
/// the web Create/Edit Customer drawer (type toggle, corporate-only company/tax,
/// contact, address, status on edit, assignee).
class CustomerFormScreen extends ConsumerStatefulWidget {
  const CustomerFormScreen({super.key, required this.mode, this.customer})
    : assert(
        mode == CustomerFormMode.create || customer != null,
        'edit requires the source customer',
      );

  final CustomerFormMode mode;
  final Customer? customer;

  @override
  ConsumerState<CustomerFormScreen> createState() => _CustomerFormScreenState();
}

class _CustomerFormScreenState extends ConsumerState<CustomerFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final _fullName = TextEditingController();
  final _companyName = TextEditingController();
  final _phone = TextEditingController();
  final _email = TextEditingController();
  final _taxCode = TextEditingController();
  final _address = TextEditingController();

  CustomerType _type = CustomerType.individual;
  CustomerStatus _status = CustomerStatus.active;
  String? _assigneeId;
  String? _assigneeName;

  bool _submitting = false;
  bool _autovalidate = false;

  bool get _isCreate => widget.mode == CustomerFormMode.create;
  bool get _isCorporate => _type.isCorporate;

  @override
  void initState() {
    super.initState();
    final c = widget.customer;
    if (c != null) {
      _fullName.text = c.fullName;
      _companyName.text = c.companyName ?? '';
      _phone.text = c.phone ?? '';
      _email.text = c.email ?? '';
      _taxCode.text = c.taxCode ?? '';
      _address.text = c.address ?? '';
      _type = c.customerType;
      _status = c.status;
      _assigneeId = c.assignedUserId;
      _assigneeName = c.assignedUserName;
    }
  }

  @override
  void dispose() {
    _fullName.dispose();
    _companyName.dispose();
    _phone.dispose();
    _email.dispose();
    _taxCode.dispose();
    _address.dispose();
    super.dispose();
  }

  Future<void> _pickAssignee() async {
    final selected = await showModalBottomSheet<UserSummary>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => const TaskAssigneePicker(),
    );
    if (selected != null) {
      setState(() {
        _assigneeId = selected.userId;
        _assigneeName = selected.fullName;
      });
    }
  }

  static final _emailRe = RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$');

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (_fullName.text.trim().isEmpty) {
      setState(() => _autovalidate = true);
      _snack('A name is required.');
      return;
    }
    if (_email.text.trim().isNotEmpty &&
        !_emailRe.hasMatch(_email.text.trim())) {
      setState(() => _autovalidate = true);
      _snack('Please enter a valid email address.');
      return;
    }
    setState(() => _submitting = true);
    final messenger = ScaffoldMessenger.of(context);
    final router = GoRouter.of(context);
    final repo = ref.read(customerRepositoryProvider);

    try {
      final Customer saved;
      if (_isCreate) {
        saved = await repo.createCustomer(
          CreateCustomerPayload(
            fullName: _fullName.text,
            customerType: _type,
            companyName: _isCorporate ? _companyName.text : null,
            phone: _phone.text,
            email: _email.text,
            taxCode: _isCorporate ? _taxCode.text : null,
            address: _address.text,
            assignedUserId: _assigneeId,
          ),
        );
      } else {
        saved = await repo.updateCustomer(
          widget.customer!.customerId,
          UpdateCustomerPayload(
            fullName: _fullName.text,
            customerType: _type,
            companyName: _isCorporate ? _companyName.text : null,
            phone: _phone.text,
            email: _email.text,
            taxCode: _isCorporate ? _taxCode.text : null,
            address: _address.text,
            status: _status,
            assignedUserId: _assigneeId,
          ),
        );
        ref.invalidate(customerDetailProvider(saved.customerId));
      }

      // Refresh list + stats if they are alive.
      if (ref.exists(customerListControllerProvider)) {
        unawaited(ref.read(customerListControllerProvider.notifier).refresh());
      }
      ref.invalidate(customerStatsProvider);
      messenger.showSnackBar(
        SnackBar(
          content: Text(_isCreate ? 'Customer created' : 'Customer updated'),
        ),
      );
      router.pop();
    } on AppException catch (e) {
      if (mounted) setState(() => _submitting = false);
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  void _snack(String message) => ScaffoldMessenger.of(
    context,
  ).showSnackBar(SnackBar(content: Text(message)));

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(_isCreate ? 'New customer' : 'Edit customer')),
      body: Form(
        key: _formKey,
        autovalidateMode: _autovalidate
            ? AutovalidateMode.onUserInteraction
            : AutovalidateMode.disabled,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, AppSpacing.huge),
          children: [
            Text('Customer type', style: theme.textTheme.labelLarge),
            const SizedBox(height: 8),
            SegmentedButton<CustomerType>(
              segments: const [
                ButtonSegment(
                  value: CustomerType.individual,
                  icon: Icon(Icons.person_rounded),
                  label: Text('Individual'),
                ),
                ButtonSegment(
                  value: CustomerType.corporate,
                  icon: Icon(Icons.apartment_rounded),
                  label: Text('Corporate'),
                ),
              ],
              selected: {_type},
              onSelectionChanged: _submitting
                  ? null
                  : (s) => setState(() => _type = s.first),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _fullName,
              enabled: !_submitting,
              textCapitalization: TextCapitalization.words,
              decoration: InputDecoration(
                labelText: _isCorporate
                    ? 'Representative name *'
                    : 'Full name *',
                prefixIcon: const Icon(Icons.badge_outlined),
              ),
              validator: (v) =>
                  (v == null || v.trim().isEmpty) ? 'A name is required' : null,
            ),
            if (_isCorporate) ...[
              const SizedBox(height: 16),
              TextFormField(
                controller: _companyName,
                enabled: !_submitting,
                decoration: const InputDecoration(
                  labelText: 'Company name',
                  prefixIcon: Icon(Icons.apartment_outlined),
                ),
              ),
            ],
            const SizedBox(height: 16),
            TextFormField(
              controller: _phone,
              enabled: !_submitting,
              keyboardType: TextInputType.phone,
              decoration: InputDecoration(
                labelText: _isCorporate ? 'Business phone' : 'Phone',
                prefixIcon: const Icon(Icons.phone_outlined),
              ),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _email,
              enabled: !_submitting,
              keyboardType: TextInputType.emailAddress,
              decoration: InputDecoration(
                labelText: _isCorporate ? 'Business email' : 'Email',
                prefixIcon: const Icon(Icons.email_outlined),
              ),
              validator: (v) =>
                  (v != null &&
                      v.trim().isNotEmpty &&
                      !_emailRe.hasMatch(v.trim()))
                  ? 'Invalid email'
                  : null,
            ),
            if (_isCorporate) ...[
              const SizedBox(height: 16),
              TextFormField(
                controller: _taxCode,
                enabled: !_submitting,
                decoration: const InputDecoration(
                  labelText: 'Tax code',
                  prefixIcon: Icon(Icons.receipt_long_outlined),
                ),
              ),
            ],
            const SizedBox(height: 16),
            TextFormField(
              controller: _address,
              enabled: !_submitting,
              maxLines: 2,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(
                labelText: 'Address',
                alignLabelWithHint: true,
                prefixIcon: Icon(Icons.location_on_outlined),
              ),
            ),
            if (!_isCreate) ...[
              const SizedBox(height: 20),
              Text('Status', style: theme.textTheme.labelLarge),
              const SizedBox(height: 8),
              SegmentedButton<CustomerStatus>(
                segments: const [
                  ButtonSegment(
                    value: CustomerStatus.active,
                    label: Text('Active'),
                  ),
                  ButtonSegment(
                    value: CustomerStatus.inactive,
                    label: Text('Inactive'),
                  ),
                ],
                selected: {_status},
                onSelectionChanged: _submitting
                    ? null
                    : (s) => setState(() => _status = s.first),
              ),
            ],
            const SizedBox(height: 20),
            _AssigneeTile(
              name: _assigneeName,
              onTap: _submitting ? null : _pickAssignee,
              onClear: _assigneeId == null || _submitting
                  ? null
                  : () => setState(() {
                      _assigneeId = null;
                      _assigneeName = null;
                    }),
            ),
            const SizedBox(height: 28),
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
                  : Text(_isCreate ? 'Create customer' : 'Save changes'),
            ),
          ],
        ),
      ),
    );
  }
}

class _AssigneeTile extends StatelessWidget {
  const _AssigneeTile({required this.name, required this.onTap, this.onClear});

  final String? name;
  final VoidCallback? onTap;
  final VoidCallback? onClear;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final hasValue = name != null;
    return InkWell(
      borderRadius: BorderRadius.circular(AppRadii.md),
      onTap: onTap,
      child: InputDecorator(
        decoration: InputDecoration(
          labelText: 'Assigned to',
          prefixIcon: const Icon(Icons.person_outline_rounded),
          suffixIcon: hasValue && onClear != null
              ? IconButton(
                  icon: const Icon(Icons.close_rounded, size: 18),
                  onPressed: onClear,
                )
              : const Icon(Icons.chevron_right_rounded),
        ),
        child: Text(
          hasValue ? name! : 'Unassigned',
          style: theme.textTheme.bodyLarge?.copyWith(
            color: hasValue
                ? theme.colorScheme.onSurface
                : theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ),
    );
  }
}
