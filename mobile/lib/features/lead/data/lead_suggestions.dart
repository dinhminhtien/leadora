/// Suggestion sources for the two lead fields that are stored as free text.
///
/// **Why suggestions rather than free text or a closed list.** Both columns are
/// free text on purpose — an enquiry can be for something not in the catalogue
/// yet, and refusing to record it pushes the sales rep to type it into Notes,
/// where nothing can filter on it. But left as bare text boxes they fill up with
/// spellings of the same thing. In the live data today:
///
/// * `source` holds nine values for about six real sources — `Website Inquiry`
///   (18 rows), `WEBSITE` (5) and `Website` (1) are one source written three
///   ways; so are `Referral` / `REFERRAL` and `Social Media` / `SOCIAL`.
/// * `interested_service` holds `room`, `Room`, `Rooms` and `rooms` — four
///   spellings across eight rows — beside `ok`, `f` and `aaaaa`.
///
/// Every count grouped by either column is wrong as a result, and no filter can
/// find them all. Suggestions do not forbid the tenth spelling; they make the
/// first nine stop happening. This mirrors the web's `InterestedServiceInput`.
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/network_providers.dart';

/// The enquiry channels the web offers, kept identical so the two clients do not
/// seed the column with different vocabularies.
///
/// Deliberately *not* applied as a default on the create form. The web preselects
/// "Website Inquiry", which is why it accounts for 44% of every lead on record
/// and why a genuine website enquiry can no longer be told apart from a field
/// nobody looked at. A silent default is worse than a blank: it looks like data.
const kLeadSourceOptions = <String>[
  'Website Inquiry',
  'Referral',
  'Social Media',
  'Cold Call',
  'Walk-in',
  'Event',
];

/// Shown when the catalogue is empty or unreachable, so the field is never left
/// with no guidance. A suggestion list is an aid — losing it must not block
/// recording an enquiry.
const kInterestedServiceFallback = <String>[
  'Rooms',
  'Wedding banquet',
  'Conference',
  'Event space',
  'Catering',
  'Airport transfer',
];

/// Names of the hotel's ACTIVE services, for the Interested service field.
class ServiceCatalogRepository {
  ServiceCatalogRepository(this._client);

  final ApiClient _client;

  Future<List<String>> getActiveServiceNames() {
    return _client.get<List<String>>(
      ApiPaths.productServices,
      decode: (data) {
        // The endpoint returns a bare list on some builds and a Spring page on
        // others; both shapes carry the same items. Anything else yields no
        // suggestions rather than a cast error — this list is an aid, and the
        // form has to stay usable when the catalogue is not.
        final raw = switch (data) {
          final Map<dynamic, dynamic> map when map['content'] is List =>
            map['content'] as List,
          final List list => list,
          _ => const <dynamic>[],
        };
        return raw
            .whereType<Map<String, dynamic>>()
            .where((s) => s['status'] == 'ACTIVE')
            .map((s) => (s['name'] as String? ?? '').trim())
            .where((name) => name.isNotEmpty)
            .toList();
      },
    );
  }
}

final serviceCatalogRepositoryProvider = Provider<ServiceCatalogRepository>((
  ref,
) {
  return ServiceCatalogRepository(ref.watch(apiClientProvider));
});

/// Service-name suggestions, falling back to [kInterestedServiceFallback] rather
/// than surfacing an error: the form must stay usable when the catalogue is not.
final interestedServiceSuggestionsProvider = FutureProvider<List<String>>((
  ref,
) async {
  try {
    final names = await ref
        .watch(serviceCatalogRepositoryProvider)
        .getActiveServiceNames();
    return names.isEmpty ? kInterestedServiceFallback : names;
  } catch (_) {
    return kInterestedServiceFallback;
  }
});
