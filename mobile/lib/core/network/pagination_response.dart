import 'api_exception.dart';

/// Dart mirror of a Spring `Page<T>` serialized inside `ApiResponse.data`.
///
/// The backend returns Spring Data's native page shape (see
/// `CustomerController#listCustomers` → `ApiResponse<Page<CustomerResponse>>`):
///
/// ```json
/// { "content": [ ... ], "number": 0, "size": 10,
///   "totalElements": 42, "totalPages": 5,
///   "first": true, "last": false, "numberOfElements": 10, "empty": false }
/// ```
///
/// We read the current index from `number` but fall back to `page` so the same
/// model also fits any hand-rolled paged DTO on the backend without changes.
class PaginationResponse<T> {
  const PaginationResponse({
    required this.items,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    required this.isFirst,
    required this.isLast,
  });

  final List<T> items;

  /// Zero-based index of the current page.
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final bool isFirst;
  final bool isLast;

  /// True when there is at least one more page to fetch — drives infinite
  /// scroll "load more" logic.
  bool get hasMore => !isLast;

  int get nextPage => page + 1;

  bool get isEmpty => items.isEmpty;

  factory PaginationResponse.parse(
    Object? data,
    T Function(Object? item) decodeItem,
  ) {
    if (data is! Map<String, dynamic>) {
      throw SerializationException(
        debugDetail: 'Expected a paged object, got ${data.runtimeType}',
      );
    }
    final rawContent = data['content'];
    if (rawContent is! List) {
      throw SerializationException(
        debugDetail: 'Paged "content" was ${rawContent.runtimeType}, not a List',
      );
    }
    final items = rawContent.map(decodeItem).toList(growable: false);

    final page = (data['number'] ?? data['page'] ?? 0) as int;
    final totalPages = (data['totalPages'] ?? 0) as int;

    return PaginationResponse<T>(
      items: items,
      page: page,
      size: (data['size'] ?? items.length) as int,
      totalElements: (data['totalElements'] ?? items.length) as int,
      totalPages: totalPages,
      isFirst: (data['first'] ?? page == 0) as bool,
      isLast: (data['last'] ?? page >= totalPages - 1) as bool,
    );
  }

  /// Build an empty page (used as an initial state before first fetch).
  factory PaginationResponse.empty({int size = 10}) => PaginationResponse<T>(
        items: const [],
        page: 0,
        size: size,
        totalElements: 0,
        totalPages: 0,
        isFirst: true,
        isLast: true,
      );

  /// Append the next page's items onto this one — for infinite-scroll
  /// accumulation. Metadata is taken from the newer page.
  PaginationResponse<T> merge(PaginationResponse<T> next) {
    return PaginationResponse<T>(
      items: [...items, ...next.items],
      page: next.page,
      size: next.size,
      totalElements: next.totalElements,
      totalPages: next.totalPages,
      isFirst: isFirst,
      isLast: next.isLast,
    );
  }
}
