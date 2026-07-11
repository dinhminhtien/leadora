import 'package:flutter/material.dart';
import 'package:skeletonizer/skeletonizer.dart';

import '../../core/theme/app_dimens.dart';

/// Shimmering placeholder rows shown while a list's first page loads.
///
/// Pass the real row widget bound to dummy data via [itemBuilder] so the
/// skeleton has exactly the shape of the content that replaces it — a generic
/// grey box makes the list visibly jump when data lands.
///
/// Non-scrollable on purpose: it never sits inside a [RefreshIndicator], and a
/// scrollable skeleton lets the user fling an empty list.
class ListSkeleton extends StatelessWidget {
  const ListSkeleton({
    super.key,
    required this.itemBuilder,
    this.itemCount = 6,
    this.separatorHeight = AppSpacing.md,
    this.padding,
  });

  final WidgetBuilder itemBuilder;
  final int itemCount;
  final double separatorHeight;
  final EdgeInsets? padding;

  @override
  Widget build(BuildContext context) {
    return Skeletonizer(
      child: ListView.separated(
        physics: const NeverScrollableScrollPhysics(),
        padding:
            padding ??
            const EdgeInsets.fromLTRB(
              AppSpacing.lg,
              AppSpacing.xs,
              AppSpacing.lg,
              AppSpacing.lg,
            ),
        itemCount: itemCount,
        separatorBuilder: (_, _) => SizedBox(height: separatorHeight),
        itemBuilder: (context, _) => itemBuilder(context),
      ),
    );
  }
}
