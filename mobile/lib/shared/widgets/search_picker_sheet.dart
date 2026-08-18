import 'dart:async';

import 'package:flutter/material.dart';

import '../../core/network/api_exception.dart';
import '../../core/network/pagination_response.dart';
import '../../core/theme/app_dimens.dart';
import 'empty_state.dart';

/// One page of picker results, plus whether another exists.
///
/// Deliberately not [PaginationResponse] itself: some endpoints this picker serves return
/// a plain array. Callers of a paged endpoint convert with [SearchPickerPage.fromPage].
class SearchPickerPage<T> {
  const SearchPickerPage({required this.items, required this.hasMore});

  final List<T> items;
  final bool hasMore;

  factory SearchPickerPage.fromPage(PaginationResponse<T> page) =>
      SearchPickerPage(items: page.items, hasMore: page.hasMore);
}

/// Fetches one page. [page] is zero-based; [query] is the trimmed search term, empty
/// before the user has typed anything.
typedef SearchPickerFetch<T> =
    Future<SearchPickerPage<T>> Function(String query, int page);

/// A search-first, paginated picker in a bottom sheet. Pops with the chosen item, or
/// `null` if dismissed.
///
/// Built for one-handed use: the search field and the results both sit in the lower two
/// thirds of the screen where the thumb reaches, the keyboard opens on entry, and the
/// sheet grows with the viewport instead of assuming a phone.
///
/// It never decides *what* is selectable — [fetch] does, and by convention that means the
/// server does. Screens that filter the returned page have reintroduced the client-side
/// rule this widget exists to remove.
class SearchPickerSheet<T> extends StatefulWidget {
  const SearchPickerSheet({
    super.key,
    required this.title,
    required this.fetch,
    required this.itemBuilder,
    this.subtitle,
    this.searchHint = 'Search…',
    this.emptyMessage = 'No matches. Try a different search term.',
    this.noOptionsMessage = 'Nothing available to pick.',
    this.leadingIcon = Icons.search_rounded,
    this.debounce = AppDurations.debounce,
    this.selectedKey,
    this.keyOf,
  }) : assert(
         selectedKey == null || keyOf != null,
         'Highlighting the current selection needs keyOf to identify rows',
       );

  final String title;
  final String? subtitle;
  final String searchHint;
  final String emptyMessage;

  /// Shown when the *unsearched* first page comes back empty — i.e. there is genuinely
  /// nothing to pick, as opposed to nothing matching a term.
  final String noOptionsMessage;

  final IconData leadingIcon;
  final Duration debounce;

  final SearchPickerFetch<T> fetch;

  /// Renders one row. The picker supplies the tap handling and selection highlight.
  final Widget Function(BuildContext context, T item, bool isSelected) itemBuilder;

  /// Key of the item currently selected by the caller, so it can be marked in the list.
  final String? selectedKey;
  final String Function(T item)? keyOf;

  @override
  State<SearchPickerSheet<T>> createState() => _SearchPickerSheetState<T>();
}

class _SearchPickerSheetState<T> extends State<SearchPickerSheet<T>> {
  final _searchController = TextEditingController();
  final _scrollController = ScrollController();

  Timer? _debounce;
  String _query = '';

  final List<T> _items = [];
  int _page = 0;
  bool _hasMore = false;
  bool _loading = true;
  bool _loadingMore = false;
  Object? _error;

  /// Guards against out-of-order responses: a slow first page for an abandoned term must
  /// not overwrite the results of the term the user has since typed.
  int _requestId = 0;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
    // Open on the unsearched first page rather than a blank panel — with a sensible
    // server-side ordering that is often the row the user wanted, at zero taps.
    _search('');
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchController.dispose();
    _scrollController.removeListener(_onScroll);
    _scrollController.dispose();
    super.dispose();
  }

  void _onQueryChanged(String value) {
    // Rebuild so the clear affordance tracks the field.
    setState(() {});
    _debounce?.cancel();
    _debounce = Timer(widget.debounce, () => _search(value.trim()));
  }

  void _clearQuery() {
    _debounce?.cancel();
    _searchController.clear();
    _search('');
  }

  Future<void> _search(String query) async {
    final id = ++_requestId;
    setState(() {
      _query = query;
      _loading = true;
      _error = null;
    });
    try {
      final result = await widget.fetch(query, 0);
      if (!mounted || id != _requestId) return;
      setState(() {
        _items
          ..clear()
          ..addAll(result.items);
        _page = 0;
        _hasMore = result.hasMore;
        _loading = false;
      });
    } catch (e) {
      if (!mounted || id != _requestId) return;
      setState(() {
        _items.clear();
        _hasMore = false;
        _error = e;
        _loading = false;
      });
    }
  }

  void _onScroll() {
    if (!_scrollController.hasClients) return;
    final position = _scrollController.position;
    // Start the next page a screenful early so scrolling never visibly stalls.
    if (position.pixels >= position.maxScrollExtent - 240) _loadMore();
  }

  Future<void> _loadMore() async {
    if (_loading || _loadingMore || !_hasMore) return;
    final id = _requestId;
    final next = _page + 1;
    setState(() => _loadingMore = true);
    try {
      final result = await widget.fetch(_query, next);
      if (!mounted || id != _requestId) return;
      setState(() {
        // Re-key rather than blind-append: a row that shifted pages between requests
        // would otherwise appear twice.
        final keyOf = widget.keyOf;
        if (keyOf == null) {
          _items.addAll(result.items);
        } else {
          final seen = _items.map(keyOf).toSet();
          _items.addAll(result.items.where((i) => !seen.contains(keyOf(i))));
        }
        _page = next;
        _hasMore = result.hasMore;
        _loadingMore = false;
      });
    } catch (_) {
      if (!mounted || id != _requestId) return;
      // A failed page is not a failed search — keep what is on screen and stop paging.
      setState(() {
        _hasMore = false;
        _loadingMore = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final media = MediaQuery.of(context);

    // Fill most of the viewport but never all of it, and never less than a usable list.
    // Ties the sheet to the *available* height, so it adapts to landscape, foldables and
    // tablets instead of assuming a portrait phone.
    final maxHeight = media.size.height - media.padding.top - AppSpacing.huge;
    final height = maxHeight.clamp(240.0, media.size.height * 0.85);

    return Padding(
      padding: EdgeInsets.only(bottom: media.viewInsets.bottom),
      child: SizedBox(
        height: height,
        child: SafeArea(
          top: false,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(
                  AppSpacing.lg,
                  0,
                  AppSpacing.lg,
                  AppSpacing.sm,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(widget.title, style: theme.textTheme.titleMedium),
                    if (widget.subtitle != null) ...[
                      const SizedBox(height: AppSpacing.xs),
                      Text(
                        widget.subtitle!,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
                child: TextField(
                  controller: _searchController,
                  autofocus: true,
                  onChanged: _onQueryChanged,
                  textInputAction: TextInputAction.search,
                  // The search terms are names and codes; neither autocorrect nor
                  // sentence-casing helps, and both get in the way of fast entry.
                  autocorrect: false,
                  textCapitalization: TextCapitalization.none,
                  decoration: InputDecoration(
                    hintText: widget.searchHint,
                    prefixIcon: const Icon(Icons.search_rounded),
                    isDense: true,
                    suffixIcon: _searchController.text.isEmpty
                        ? null
                        : IconButton(
                            tooltip: 'Clear search',
                            icon: const Icon(Icons.close_rounded),
                            onPressed: _clearQuery,
                          ),
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.sm),
              Expanded(child: _buildResults(theme, scheme)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildResults(ThemeData theme, ColorScheme scheme) {
    if (_loading) {
      return ListView.builder(
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
        itemCount: 6,
        itemBuilder: (_, _) => const _RowSkeleton(),
      );
    }

    if (_error != null) {
      final message = _error is AppException
          ? (_error as AppException).message
          : 'Search failed. Check your connection and try again.';
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.xl),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.error_outline_rounded,
                size: AppIconSize.display,
                color: scheme.error,
              ),
              const SizedBox(height: AppSpacing.sm),
              Text(message, textAlign: TextAlign.center, style: theme.textTheme.bodyMedium),
              const SizedBox(height: AppSpacing.sm),
              TextButton(
                onPressed: () => _search(_query),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      );
    }

    if (_items.isEmpty) {
      return EmptyState(
        icon: _query.isEmpty ? widget.leadingIcon : Icons.search_off_rounded,
        message: _query.isEmpty ? widget.noOptionsMessage : widget.emptyMessage,
      );
    }

    final keyOf = widget.keyOf;
    return ListView.builder(
      controller: _scrollController,
      padding: const EdgeInsets.only(bottom: AppSpacing.lg),
      // One extra row for the paging spinner when another page is coming.
      itemCount: _items.length + (_hasMore ? 1 : 0),
      itemBuilder: (context, index) {
        if (index >= _items.length) {
          return const Padding(
            padding: EdgeInsets.symmetric(vertical: AppSpacing.lg),
            child: Center(
              child: SizedBox(
                width: AppIconSize.lg,
                height: AppIconSize.lg,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            ),
          );
        }
        final item = _items[index];
        final isSelected =
            keyOf != null && widget.selectedKey != null && keyOf(item) == widget.selectedKey;
        return InkWell(
          onTap: () => Navigator.of(context).pop(item),
          child: widget.itemBuilder(context, item, isSelected),
        );
      },
    );
  }
}

/// Shimmer-less placeholder row — a calm grey block beats a spinner for a list, because
/// it shows how much is coming.
class _RowSkeleton extends StatelessWidget {
  const _RowSkeleton();

  @override
  Widget build(BuildContext context) {
    final base = Theme.of(context).colorScheme.surfaceContainerHighest;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.md),
      child: Row(
        children: [
          Container(
            width: AppSpacing.huge,
            height: AppSpacing.huge,
            decoration: BoxDecoration(color: base, shape: BoxShape.circle),
          ),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  height: AppSpacing.md,
                  decoration: BoxDecoration(
                    color: base,
                    borderRadius: BorderRadius.circular(AppRadii.sm),
                  ),
                ),
                const SizedBox(height: AppSpacing.sm),
                FractionallySizedBox(
                  widthFactor: 0.55,
                  child: Container(
                    height: AppSpacing.sm,
                    decoration: BoxDecoration(
                      color: base,
                      borderRadius: BorderRadius.circular(AppRadii.sm),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
