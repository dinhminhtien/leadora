import 'package:intl/intl.dart';

/// Presentation-layer formatting helpers. Kept framework-free so they can be
/// unit tested and reused by every feature without pulling in widgets.
class Formatters {
  const Formatters._();

  static final DateFormat _date = DateFormat('dd MMM yyyy');
  static final DateFormat _shortDate = DateFormat('dd MMM');
  static final DateFormat _dateTime = DateFormat('dd MMM yyyy · HH:mm');
  static final DateFormat _time = DateFormat('HH:mm');
  static final DateFormat _monthYear = DateFormat('MMMM yyyy');
  static final NumberFormat _currency = NumberFormat.currency(
    locale: 'vi_VN',
    symbol: '₫',
    decimalDigits: 0,
  );
  static final NumberFormat _compact = NumberFormat.compact();

  static String date(DateTime? value) =>
      value == null ? '—' : _date.format(value.toLocal());

  /// Day + month without the year — for tight rows where the year is implied.
  static String shortDate(DateTime? value) =>
      value == null ? '—' : _shortDate.format(value.toLocal());

  static String dateTime(DateTime? value) =>
      value == null ? '—' : _dateTime.format(value.toLocal());

  static String time(DateTime? value) =>
      value == null ? '—' : _time.format(value.toLocal());

  /// e.g. `July 2026` — used for grouping timeline entries by month.
  static String monthYear(DateTime? value) =>
      value == null ? '—' : _monthYear.format(value.toLocal());

  static String money(num? value) =>
      value == null ? '—' : _currency.format(value);

  static String compact(num? value) =>
      value == null ? '0' : _compact.format(value);

  /// Human "time ago" / "in X" phrasing for timelines and due dates.
  static String relative(DateTime? value, {DateTime? now}) {
    if (value == null) return '—';
    final ref = now ?? DateTime.now();
    final local = value.toLocal();
    final diff = local.difference(ref);
    final past = diff.isNegative;
    final d = diff.abs();

    String phrase;
    if (d.inMinutes < 1) {
      return 'just now';
    } else if (d.inMinutes < 60) {
      phrase = '${d.inMinutes}m';
    } else if (d.inHours < 24) {
      phrase = '${d.inHours}h';
    } else if (d.inDays < 30) {
      phrase = '${d.inDays}d';
    } else {
      return date(local);
    }
    return past ? '$phrase ago' : 'in $phrase';
  }

  /// `NEW` / `IN_PROGRESS` → `New` / `In progress`.
  static String humanizeEnum(String? raw) {
    if (raw == null || raw.isEmpty) return '—';
    final words = raw.toLowerCase().split(RegExp('[_\\s]+'));
    if (words.isEmpty) return raw;
    final first = words.first;
    final head = first.isEmpty
        ? first
        : first[0].toUpperCase() + first.substring(1);
    return [head, ...words.skip(1)].join(' ');
  }

  /// Initials for avatar fallbacks (max two letters).
  static String initials(String? name) {
    final parts = (name ?? '')
        .trim()
        .split(RegExp('\\s+'))
        .where((p) => p.isNotEmpty)
        .toList();
    if (parts.isEmpty) return '?';
    if (parts.length == 1) {
      final one = parts.first;
      return (one.length >= 2 ? one.substring(0, 2) : one).toUpperCase();
    }
    return (parts.first[0] + parts.last[0]).toUpperCase();
  }
}
