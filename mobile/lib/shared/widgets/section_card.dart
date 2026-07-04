import 'package:flutter/material.dart';

import '../formatters.dart';

/// A titled surface card used to group detail fields on detail screens.
class SectionCard extends StatelessWidget {
  const SectionCard({
    super.key,
    this.title,
    this.icon,
    this.trailing,
    required this.child,
    this.padding = const EdgeInsets.all(16),
  });

  final String? title;
  final IconData? icon;
  final Widget? trailing;
  final Widget child;
  final EdgeInsets padding;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      elevation: 0,
      margin: EdgeInsets.zero,
      color: theme.colorScheme.surfaceContainerLow,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: theme.colorScheme.outlineVariant.withValues(alpha: 0.5)),
      ),
      child: Padding(
        padding: padding,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (title != null) ...[
              Row(
                children: [
                  if (icon != null) ...[
                    Icon(icon, size: 18, color: theme.colorScheme.primary),
                    const SizedBox(width: 8),
                  ],
                  Expanded(
                    child: Text(
                      title!,
                      style: theme.textTheme.titleSmall
                          ?.copyWith(fontWeight: FontWeight.w700),
                    ),
                  ),
                  ?trailing,
                ],
              ),
              const SizedBox(height: 12),
            ],
            child,
          ],
        ),
      ),
    );
  }
}

/// A label → value row for detail views. Renders nothing when [value] is empty.
class InfoRow extends StatelessWidget {
  const InfoRow({
    super.key,
    required this.label,
    required this.value,
    this.icon,
  });

  final String label;
  final String? value;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final v = (value == null || value!.trim().isEmpty) ? '—' : value!;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 16, color: theme.colorScheme.onSurfaceVariant),
            const SizedBox(width: 10),
          ],
          SizedBox(
            width: 108,
            child: Text(
              label,
              style: theme.textTheme.bodySmall
                  ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
          ),
          Expanded(
            child: Text(
              v,
              style: theme.textTheme.bodyMedium
                  ?.copyWith(fontWeight: FontWeight.w500),
            ),
          ),
        ],
      ),
    );
  }
}

/// Circular initials avatar with a deterministic tint from the name.
class AppAvatar extends StatelessWidget {
  const AppAvatar({super.key, required this.name, this.radius = 20, this.imageUrl});

  final String name;
  final double radius;
  final String? imageUrl;

  @override
  Widget build(BuildContext context) {
    final tint = Colors.primaries[name.hashCode.abs() % Colors.primaries.length];
    if (imageUrl != null && imageUrl!.isNotEmpty) {
      return CircleAvatar(radius: radius, backgroundImage: NetworkImage(imageUrl!));
    }
    return CircleAvatar(
      radius: radius,
      backgroundColor: tint.withValues(alpha: 0.18),
      child: Text(
        Formatters.initials(name),
        style: TextStyle(
          color: HSLColor.fromColor(tint).withLightness(0.35).toColor(),
          fontWeight: FontWeight.w700,
          fontSize: radius * 0.7,
        ),
      ),
    );
  }
}
