import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/storage/local_avatar_store.dart';
import '../../core/theme/app_dimens.dart';
import '../formatters.dart';

/// A titled surface card used to group detail fields on detail screens.
class SectionCard extends StatelessWidget {
  const SectionCard({
    super.key,
    this.title,
    this.icon,
    this.trailing,
    required this.child,
    this.padding = const EdgeInsets.all(AppSpacing.lg),
  });

  final String? title;
  final IconData? icon;
  final Widget? trailing;
  final Widget child;
  final EdgeInsets padding;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Card(
      child: Padding(
        padding: padding,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (title != null) ...[
              Row(
                children: [
                  if (icon != null) ...[
                    Container(
                      padding: const EdgeInsets.all(AppSpacing.sm),
                      decoration: BoxDecoration(
                        color: scheme.primary.withValues(alpha: 0.10),
                        borderRadius: BorderRadius.circular(AppRadii.sm),
                      ),
                      child: Icon(icon, size: 16, color: scheme.primary),
                    ),
                    const SizedBox(width: AppSpacing.md),
                  ],
                  Expanded(
                    child: Text(
                      title!,
                      style: theme.textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  ?trailing,
                ],
              ),
              const SizedBox(height: AppSpacing.lg),
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
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm),
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
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
          Expanded(
            child: Text(
              v,
              style: theme.textTheme.bodyMedium?.copyWith(
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Circular avatar: remote photo when a usable URL is present, otherwise
/// initials with a deterministic tint from the name.
///
/// Besides http(s) URLs it resolves the `local-storage-avatar://<userId>`
/// placeholder the web client writes for browser-local uploads — on this
/// device the bytes come from [LocalAvatarStore]; on devices that don't hold
/// the image it falls back to initials, exactly like the web does. Network
/// failures also fall back instead of leaving a broken circle.
class AppAvatar extends ConsumerWidget {
  const AppAvatar({
    super.key,
    required this.name,
    this.radius = 20,
    this.imageUrl,
  });

  final String name;
  final double radius;
  final String? imageUrl;

  static bool _isLoadable(String? url) {
    if (url == null || url.isEmpty) return false;
    final uri = Uri.tryParse(url);
    return uri != null && (uri.isScheme('http') || uri.isScheme('https'));
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final fallback = _initialsCircle();

    final localUserId = LocalAvatarStore.userIdFrom(imageUrl);
    if (localUserId != null) {
      final bytes = ref
          .watch(localAvatarBytesProvider(localUserId))
          .valueOrNull;
      if (bytes == null) return fallback;
      return CircleAvatar(
        radius: radius,
        backgroundImage: MemoryImage(bytes),
        backgroundColor: Colors.transparent,
      );
    }

    if (!_isLoadable(imageUrl)) return fallback;
    return CircleAvatar(
      radius: radius,
      // Initials stay visible behind the photo while it loads / if it fails.
      backgroundColor: Colors.transparent,
      child: ClipOval(
        child: CachedNetworkImage(
          imageUrl: imageUrl!,
          width: radius * 2,
          height: radius * 2,
          fit: BoxFit.cover,
          fadeInDuration: const Duration(milliseconds: 200),
          placeholder: (_, _) => fallback,
          errorWidget: (_, _, _) => fallback,
        ),
      ),
    );
  }

  Widget _initialsCircle() {
    final tint =
        Colors.primaries[name.hashCode.abs() % Colors.primaries.length];
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
