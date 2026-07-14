import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../shared/formatters.dart';
import '../../data/interaction_models.dart';
import '../../data/interaction_repository.dart';

/// Edit an existing interaction — mirrors the backend `PUT /interaction-timeline/{id}`
/// (partial edit of type/description/occurredAt; the linked record can't move).
///
/// Prefilled from the [entry] handed in by the detail screen. Pops `true` on a
/// successful save so the caller can refresh the detail, its history and the
/// timeline.
class EditInteractionScreen extends ConsumerStatefulWidget {
  const EditInteractionScreen({super.key, required this.entry});

  final InteractionTimelineEntry entry;

  @override
  ConsumerState<EditInteractionScreen> createState() =>
      _EditInteractionScreenState();
}

class _EditInteractionScreenState extends ConsumerState<EditInteractionScreen> {
  late InteractionType _type;
  late DateTime _occurredAt;
  late final TextEditingController _descriptionController;
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    _type = widget.entry.type;
    _occurredAt = widget.entry.occurredAt ?? DateTime.now();
    _descriptionController = TextEditingController(
      text: widget.entry.description,
    );
  }

  @override
  void dispose() {
    _descriptionController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Edit interaction')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.lg,
          AppSpacing.lg,
          AppSpacing.lg,
          AppSpacing.xxxl,
        ),
        children: [
          if (widget.entry.linkedName != null &&
              widget.entry.linkedName != 'N/A') ...[
            Text('For', style: Theme.of(context).textTheme.labelMedium),
            const SizedBox(height: AppSpacing.xs),
            Text(
              widget.entry.linkedName!,
              style: Theme.of(
                context,
              ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: AppSpacing.lg),
          ],
          Text('Type', style: Theme.of(context).textTheme.labelMedium),
          const SizedBox(height: AppSpacing.sm),
          Wrap(
            spacing: AppSpacing.sm,
            runSpacing: AppSpacing.sm,
            children: [
              for (final t in InteractionType.values)
                ChoiceChip(
                  avatar: Icon(t.icon, size: AppIconSize.sm),
                  label: Text(t.label),
                  selected: _type == t,
                  onSelected: (_) => setState(() => _type = t),
                ),
            ],
          ),
          const SizedBox(height: AppSpacing.lg),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.event_outlined),
            title: const Text('Occurred at'),
            subtitle: Text(Formatters.dateTime(_occurredAt)),
            trailing: const Icon(Icons.edit_calendar_outlined),
            onTap: _pickOccurredAt,
          ),
          const SizedBox(height: AppSpacing.md),
          TextField(
            controller: _descriptionController,
            minLines: 4,
            maxLines: 8,
            decoration: const InputDecoration(
              labelText: 'Description',
              hintText: 'What happened in this interaction?',
              border: OutlineInputBorder(),
              alignLabelWithHint: true,
            ),
          ),
          const SizedBox(height: AppSpacing.xxl),
          FilledButton.icon(
            onPressed: _submitting ? null : _submit,
            icon: _submitting
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.check_rounded),
            label: const Text('Save changes'),
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
    final messenger = ScaffoldMessenger.of(context);
    if (description.isEmpty) {
      messenger.showSnackBar(
        const SnackBar(content: Text('Enter a description')),
      );
      return;
    }

    setState(() => _submitting = true);
    final navigator = Navigator.of(context);
    try {
      await ref
          .read(interactionRepositoryProvider)
          .updateInteraction(
            widget.entry.id,
            UpdateInteractionPayload(
              type: _type,
              description: description,
              occurredAt: _occurredAt,
            ),
          );
      messenger.showSnackBar(
        const SnackBar(content: Text('Interaction updated')),
      );
      navigator.pop(true);
    } on AppException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }
}
