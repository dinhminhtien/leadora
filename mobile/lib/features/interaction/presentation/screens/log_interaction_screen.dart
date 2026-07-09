import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../data/interaction_models.dart';
import '../../data/interaction_repository.dart';
import '../providers/interaction_providers.dart';

/// Log Customer Interaction Note + Record Customer Meeting Summary on Mobile.
///
/// Both use cases share the same backend endpoint
/// (`POST /interaction-timeline`), differentiated only by `type` — this
/// screen covers both via [initialType] ("note" or "meeting").
class LogInteractionScreen extends ConsumerStatefulWidget {
  const LogInteractionScreen({
    super.key,
    required this.linkedType,
    required this.linkedId,
    this.linkedName,
    this.initialType,
  });

  final String linkedType;
  final String linkedId;
  final String? linkedName;
  final String? initialType;

  @override
  ConsumerState<LogInteractionScreen> createState() =>
      _LogInteractionScreenState();
}

class _LogInteractionScreenState extends ConsumerState<LogInteractionScreen> {
  late InteractionType _type;
  late DateTime _occurredAt;
  final _descriptionController = TextEditingController();
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    _type = InteractionType.fromWire(widget.initialType);
    _occurredAt = DateTime.now();
  }

  @override
  void dispose() {
    _descriptionController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isMeeting = _type == InteractionType.meeting;
    return Scaffold(
      appBar: AppBar(
        title: Text(isMeeting ? 'Record meeting summary' : 'Log interaction'),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, AppSpacing.xxxl),
        children: [
          if (widget.linkedName != null &&
              widget.linkedName!.trim().isNotEmpty) ...[
            Text('For', style: Theme.of(context).textTheme.labelMedium),
            const SizedBox(height: 4),
            Text(
              widget.linkedName!,
              style: Theme.of(
                context,
              ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 16),
          ],
          Text('Type', style: Theme.of(context).textTheme.labelMedium),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final t in InteractionType.values)
                ChoiceChip(
                  avatar: Icon(t.icon, size: 16),
                  label: Text(t.label),
                  selected: _type == t,
                  onSelected: (_) => setState(() => _type = t),
                ),
            ],
          ),
          const SizedBox(height: 16),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.event_outlined),
            title: const Text('Occurred at'),
            subtitle: Text(Formatters.dateTime(_occurredAt)),
            trailing: const Icon(Icons.edit_calendar_outlined),
            onTap: _pickOccurredAt,
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _descriptionController,
            minLines: 4,
            maxLines: 8,
            decoration: InputDecoration(
              labelText: isMeeting ? 'Meeting summary' : 'Note',
              hintText: isMeeting
                  ? 'Summarize what was discussed and agreed…'
                  : 'What happened in this interaction?',
              border: const OutlineInputBorder(),
              alignLabelWithHint: true,
            ),
          ),
          const SizedBox(height: 24),
          FilledButton.icon(
            onPressed: _submitting ? null : _submit,
            icon: _submitting
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.check_rounded),
            label: Text(isMeeting ? 'Save summary' : 'Save note'),
            style: FilledButton.styleFrom(
              minimumSize: const Size.fromHeight(50),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _pickOccurredAt() async {
    final date = await showDatePicker(
      context: context,
      initialDate: _occurredAt,
      firstDate: DateTime.now().subtract(const Duration(days: 365)),
      lastDate: DateTime.now().add(const Duration(days: 1)),
    );
    if (date == null || !mounted) return;
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(_occurredAt),
    );
    if (time == null) return;
    setState(() {
      _occurredAt = DateTime(
        date.year,
        date.month,
        date.day,
        time.hour,
        time.minute,
      );
    });
  }

  Future<void> _submit() async {
    final description = _descriptionController.text.trim();
    if (description.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            _type == InteractionType.meeting
                ? 'Enter a meeting summary'
                : 'Enter a note',
          ),
        ),
      );
      return;
    }

    setState(() => _submitting = true);
    final messenger = ScaffoldMessenger.of(context);
    final navigator = Navigator.of(context);
    try {
      final payload = CreateInteractionPayload(
        type: _type,
        description: description,
        occurredAt: _occurredAt,
        leadId: widget.linkedType == 'lead' ? widget.linkedId : null,
        customerId: widget.linkedType == 'customer' ? widget.linkedId : null,
        dealId: widget.linkedType == 'deal' ? widget.linkedId : null,
      );
      await ref.read(interactionRepositoryProvider).createInteraction(payload);
      ref.invalidate(interactionTimelineProvider(widget.linkedId));
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            _type == InteractionType.meeting
                ? 'Meeting summary saved'
                : 'Note saved',
          ),
        ),
      );
      navigator.pop(true);
    } on AppException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }
}
