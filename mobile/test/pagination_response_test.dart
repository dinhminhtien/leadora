import 'package:flutter_test/flutter_test.dart';
import 'package:leadora_mobile/core/network/pagination_response.dart';

void main() {
  int decodeInt(Object? item) => item as int;

  group('PaginationResponse.parse', () {
    test('parses the Spring Boot 3.3+ nested PagedModel shape', () {
      // This is the shape the Leadora backend actually returns.
      final data = {
        'content': [1, 2, 3],
        'page': {
          'size': 15,
          'number': 0,
          'totalElements': 3,
          'totalPages': 1,
        },
      };
      final res = PaginationResponse<int>.parse(data, decodeInt);
      expect(res.items, [1, 2, 3]);
      expect(res.page, 0);
      expect(res.size, 15);
      expect(res.totalElements, 3);
      expect(res.totalPages, 1);
      expect(res.isFirst, isTrue);
      expect(res.isLast, isTrue);
      expect(res.hasMore, isFalse);
    });

    test('empty nested page is a valid last page (no crash)', () {
      final data = {
        'content': <Object?>[],
        'page': {'size': 15, 'number': 0, 'totalElements': 0, 'totalPages': 0},
      };
      final res = PaginationResponse<int>.parse(data, decodeInt);
      expect(res.items, isEmpty);
      expect(res.isLast, isTrue);
      expect(res.hasMore, isFalse);
    });

    test('nested shape reports more pages available', () {
      final data = {
        'content': [1, 2],
        'page': {'size': 2, 'number': 0, 'totalElements': 5, 'totalPages': 3},
      };
      final res = PaginationResponse<int>.parse(data, decodeInt);
      expect(res.isLast, isFalse);
      expect(res.hasMore, isTrue);
      expect(res.nextPage, 1);
    });

    test('still parses the legacy flat PageImpl shape', () {
      final data = {
        'content': [1, 2],
        'number': 1,
        'size': 2,
        'totalElements': 4,
        'totalPages': 2,
        'first': false,
        'last': true,
      };
      final res = PaginationResponse<int>.parse(data, decodeInt);
      expect(res.page, 1);
      expect(res.size, 2);
      expect(res.totalElements, 4);
      expect(res.isFirst, isFalse);
      expect(res.isLast, isTrue);
    });
  });
}
