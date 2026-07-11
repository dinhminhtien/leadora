import 'package:flutter/material.dart';
import 'package:skeletonizer/skeletonizer.dart';

import '../../core/theme/app_dimens.dart';
import 'section_card.dart';

/// Shimmering placeholder for a detail screen: a title, a chip row, then a
/// stack of section cards — the shape every detail screen in the app shares.
///
/// A bare spinner on a detail screen reads as a dead screen, and once data
/// lands the layout jumps. This keeps the skeleton the same silhouette.
class DetailSkeleton extends StatelessWidget {
  const DetailSkeleton({super.key, this.sectionCount = 3, this.rowsPerSection = 3});

  final int sectionCount;
  final int rowsPerSection;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Skeletonizer(
      child: ListView(
        physics: const NeverScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.lg,
          AppSpacing.lg,
          AppSpacing.lg,
          AppSpacing.xxxl,
        ),
        children: [
          Text(
            'Placeholder detail title spanning a line',
            style: theme.textTheme.titleLarge?.copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: AppSpacing.md),
          Wrap(
            spacing: AppSpacing.sm,
            runSpacing: AppSpacing.sm,
            children: const [
              _ChipBone(width: 96),
              _ChipBone(width: 72),
              _ChipBone(width: 110),
            ],
          ),
          const SizedBox(height: AppSpacing.lg),
          for (var i = 0; i < sectionCount; i++) ...[
            SectionCard(
              title: 'Placeholder section',
              icon: Icons.circle_outlined,
              child: Column(
                children: [
                  for (var r = 0; r < rowsPerSection; r++)
                    const InfoRow(
                      label: 'Placeholder label',
                      value: 'Placeholder value',
                    ),
                ],
              ),
            ),
            if (i != sectionCount - 1) const SizedBox(height: AppSpacing.md),
          ],
        ],
      ),
    );
  }
}

/// A pill-shaped bone matching [StatusChip]'s footprint.
class _ChipBone extends StatelessWidget {
  const _ChipBone({required this.width});

  final double width;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      height: 28,
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(AppRadii.pill),
      ),
    );
  }
}
