import 'dart:async';

import 'package:flutter/material.dart';

import '../../core/theme/app_dimens.dart';

/// The debounced search box used at the top of every list screen.
///
/// Owns its own debounce timer and clear button so screens do not each
/// re-implement the "cancel the pending debounce when cleared" rule — forgetting
/// it lets a stale term re-apply itself right after the field is emptied.
class AppSearchField extends StatefulWidget {
  const AppSearchField({
    super.key,
    required this.hintText,
    required this.onChanged,
    this.initialValue,
    this.debounce = AppDurations.debounce,
  });

  final String hintText;

  /// Called with the trimmed term after [debounce] elapses, and immediately
  /// (with `''`) when the user taps clear.
  final ValueChanged<String> onChanged;

  final String? initialValue;
  final Duration debounce;

  @override
  State<AppSearchField> createState() => _AppSearchFieldState();
}

class _AppSearchFieldState extends State<AppSearchField> {
  late final TextEditingController _controller = TextEditingController(
    text: widget.initialValue,
  );
  Timer? _debounce;

  @override
  void dispose() {
    _debounce?.cancel();
    _controller.dispose();
    super.dispose();
  }

  void _onChanged(String value) {
    // Rebuild so the clear (X) suffix appears/disappears as the user types.
    setState(() {});
    _debounce?.cancel();
    _debounce = Timer(widget.debounce, () {
      if (mounted) widget.onChanged(value.trim());
    });
  }

  void _clear() {
    _debounce?.cancel();
    setState(_controller.clear);
    widget.onChanged('');
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.lg,
        AppSpacing.sm,
        AppSpacing.lg,
        AppSpacing.sm,
      ),
      child: TextField(
        controller: _controller,
        onChanged: _onChanged,
        textInputAction: TextInputAction.search,
        decoration: InputDecoration(
          hintText: widget.hintText,
          prefixIcon: const Icon(Icons.search_rounded),
          isDense: true,
          suffixIcon: _controller.text.isEmpty
              ? null
              : IconButton(
                  tooltip: 'Clear search',
                  icon: const Icon(Icons.close_rounded),
                  onPressed: _clear,
                ),
        ),
      ),
    );
  }
}
