import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Device-local avatar images — the mobile mirror of the web client's
/// localStorage avatar upload (UC-5).
///
/// The backend only stores a `local-storage-avatar://<userId>` placeholder in
/// `avatarUrl`; the actual image lives on the device that uploaded it, keyed
/// by user id. Devices that don't hold the image fall back to initials, which
/// is exactly what the web does for the same placeholder.
class LocalAvatarStore {
  static const String scheme = 'local-storage-avatar';

  static String placeholderFor(String userId) => '$scheme://$userId';

  /// Extracts the user id from a placeholder URL, or null for anything else.
  static String? userIdFrom(String? url) {
    const prefix = '$scheme://';
    if (url == null || !url.startsWith(prefix)) return null;
    final id = url.substring(prefix.length);
    return id.isEmpty ? null : id;
  }

  static String _prefsKey(String userId) => 'prefs.local_avatar_$userId';

  // In-memory decode cache so avatars don't re-hit prefs on every rebuild.
  final Map<String, Uint8List?> _cache = {};

  Future<Uint8List?> load(String userId) async {
    if (_cache.containsKey(userId)) return _cache[userId];
    final prefs = await SharedPreferences.getInstance();
    final b64 = prefs.getString(_prefsKey(userId));
    Uint8List? bytes;
    if (b64 != null && b64.isNotEmpty) {
      try {
        bytes = base64Decode(b64);
      } on FormatException {
        bytes = null; // corrupted entry — treat as absent
      }
    }
    _cache[userId] = bytes;
    return bytes;
  }

  Future<void> save(String userId, Uint8List bytes) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_prefsKey(userId), base64Encode(bytes));
    _cache[userId] = bytes;
  }

  Future<void> remove(String userId) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_prefsKey(userId));
    _cache[userId] = null;
  }
}

final localAvatarStoreProvider = Provider<LocalAvatarStore>((ref) {
  return LocalAvatarStore();
});

/// The locally stored avatar bytes for a user (null when this device has
/// none). Invalidate after [LocalAvatarStore.save] / [LocalAvatarStore.remove]
/// so every avatar on screen refreshes.
final localAvatarBytesProvider = FutureProvider.family<Uint8List?, String>((
  ref,
  userId,
) {
  return ref.watch(localAvatarStoreProvider).load(userId);
});
