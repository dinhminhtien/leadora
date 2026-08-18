import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';
import 'package:supabase_flutter/supabase_flutter.dart' as supabase;
import 'package:uuid/uuid.dart';
import 'crop_screen.dart';

import '../../../../core/network/api_exception.dart';
import '../../../../core/theme/app_dimens.dart';
import '../../../../core/storage/token_store.dart';
import '../../../../shared/widgets/section_card.dart';
import '../../../auth/presentation/providers/auth_controller.dart';
import '../../data/profile_models.dart';
import '../../data/profile_repository.dart';

/// Resolves the profile for [EditProfileScreen]: prefers the instance handed
/// over via go_router `extra`, refetching `/profile/me` when that was dropped
/// (e.g. after process death) so the form still opens pre-filled.
class EditProfileLoader extends ConsumerWidget {
  const EditProfileLoader({super.key, this.initial});

  final Profile? initial;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (initial != null) return EditProfileScreen(profile: initial!);
    final async = ref.watch(myProfileProvider);
    return async.when(
      data: (profile) => EditProfileScreen(profile: profile),
      loading: () =>
          const Scaffold(body: Center(child: CircularProgressIndicator())),
      error: (error, _) => Scaffold(
        appBar: AppBar(),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.xxl),
            child: Text(
              error is AppException
                  ? error.message
                  : 'Could not load your profile.',
              textAlign: TextAlign.center,
            ),
          ),
        ),
      ),
    );
  }
}

/// UC-5 — Edit own profile. Field set and rules mirror the web Profile
/// Information card: full name (required, ≤255), phone (optional, ≤15),
/// read-only email, and an avatar upload.
///
/// The avatar goes to the shared Supabase Storage bucket `avatar`, under
/// `{userId}/{uuid}.jpg`, and only the resulting public URL is persisted — the image
/// itself never enters Postgres. The previous object is deleted once the new URL is
/// saved, so the bucket does not accumulate orphans. Identical to the web flow, so the
/// same picture follows the user across devices and is visible to colleagues.
///
/// This replaced a device-local scheme that base64'd the image into SharedPreferences
/// behind a `local-storage-avatar://<userId>` placeholder. `AppAvatar` still resolves
/// that scheme for rows whose `avatarUrl` was written before the change.
class EditProfileScreen extends ConsumerStatefulWidget {
  const EditProfileScreen({super.key, required this.profile});

  final Profile profile;

  @override
  ConsumerState<EditProfileScreen> createState() => _EditProfileScreenState();
}

class _EditProfileScreenState extends ConsumerState<EditProfileScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _fullName = TextEditingController(
    text: widget.profile.fullName,
  );
  late final TextEditingController _phone = TextEditingController(
    text: widget.profile.phone ?? '',
  );

  late String? _avatarUrl = widget.profile.avatarUrl;
  bool _submitting = false;
  bool _autovalidate = false;
  bool _pickingAvatar = false;

  @override
  void dispose() {
    _fullName.dispose();
    _phone.dispose();
    super.dispose();
  }

  Future<void> _pickAvatar() async {
    if (_pickingAvatar) return;
    _pickingAvatar = true;

    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.photo_camera_rounded),
              title: const Text('Take Photo'),
              onTap: () => Navigator.of(context).pop(ImageSource.camera),
            ),
            ListTile(
              leading: const Icon(Icons.photo_library_rounded),
              title: const Text('Choose From Gallery'),
              onTap: () => Navigator.of(context).pop(ImageSource.gallery),
            ),
            ListTile(
              leading: const Icon(Icons.close_rounded),
              title: const Text('Cancel'),
              onTap: () => Navigator.of(context).pop(),
            ),
          ],
        ),
      ),
    );

    _pickingAvatar = false;
    if (source == null || !mounted) return;

    final messenger = ScaffoldMessenger.of(context);
    final navigator = Navigator.of(context);
    try {
      final picked = await ImagePicker().pickImage(
        source: source,
      );
      if (picked == null || !mounted) return;

      final file = File(picked.path);
      final length = await file.length();
      if (length > 5 * 1024 * 1024) {
        messenger.showSnackBar(
          const SnackBar(content: Text('Avatar image size must be under 5MB.')),
        );
        return;
      }

      final Uint8List? croppedBytes = await navigator.push<Uint8List?>(
        MaterialPageRoute(
          builder: (context) => CropScreen(imageFile: file),
        ),
      );

      if (croppedBytes == null || !mounted) return;

      setState(() => _submitting = true);

      final supabaseClient = supabase.Supabase.instance.client;
      final token = ref.read(tokenStoreProvider).accessTokenSync;
      if (token != null) {
        final sessionJson = '''
        {
          "access_token": "$token",
          "refresh_token": "dummy-refresh-token",
          "expires_in": 86400,
          "token_type": "bearer",
          "user": {
            "id": "${widget.profile.userId}",
            "email": "${widget.profile.email}"
          }
        }
        ''';
        await supabaseClient.auth.recoverSession(sessionJson);
      }

      final fileName = '${widget.profile.userId}/${const Uuid().v4()}.jpg';

      await supabaseClient.storage.from('avatar').uploadBinary(
        fileName,
        croppedBytes,
        fileOptions: const supabase.FileOptions(
          contentType: 'image/jpeg',
          cacheControl: '3600',
          upsert: true,
        ),
      );

      final publicUrl = supabaseClient.storage.from('avatar').getPublicUrl(fileName);
      final oldAvatarUrl = _avatarUrl;

      await ref.read(profileRepositoryProvider).updateMyProfile(
        UpdateProfilePayload(
          fullName: _fullName.text,
          phone: _phone.text,
          avatarUrl: publicUrl,
        ),
      );

      ref.invalidate(myProfileProvider);
      ref.read(authControllerProvider.notifier).updateUserFields(
        name: _fullName.text.trim(),
        avatarUrl: publicUrl,
      );

      setState(() {
        _avatarUrl = publicUrl;
      });

      messenger.showSnackBar(
        const SnackBar(content: Text('Avatar updated successfully.')),
      );

      if (oldAvatarUrl != null && oldAvatarUrl.contains('/storage/v1/object/public/avatar/')) {
        final parts = oldAvatarUrl.split('/storage/v1/object/public/avatar/');
        if (parts.length > 1) {
          final oldPath = parts[1];
          try {
            await supabaseClient.storage.from('avatar').remove([oldPath]);
          } catch (err) {
            debugPrint('Failed to delete previous avatar: $err');
          }
        }
      }
    } catch (e) {
      messenger.showSnackBar(
        SnackBar(content: Text('Failed to update avatar: $e')),
      );
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  Future<void> _removeAvatar() async {
    setState(() => _submitting = true);
    final messenger = ScaffoldMessenger.of(context);
    try {
      final oldAvatarUrl = _avatarUrl;

      await ref.read(profileRepositoryProvider).updateMyProfile(
        UpdateProfilePayload(
          fullName: _fullName.text,
          phone: _phone.text,
          avatarUrl: null,
        ),
      );

      ref.invalidate(myProfileProvider);
      ref.read(authControllerProvider.notifier).updateUserFields(
        name: _fullName.text.trim(),
        avatarUrl: null,
        clearAvatar: true,
      );

      setState(() {
        _avatarUrl = null;
      });

      messenger.showSnackBar(
        const SnackBar(content: Text('Avatar removed successfully.')),
      );

      if (oldAvatarUrl != null && oldAvatarUrl.contains('/storage/v1/object/public/avatar/')) {
        final parts = oldAvatarUrl.split('/storage/v1/object/public/avatar/');
        if (parts.length > 1) {
          final oldPath = parts[1];
          final supabaseClient = supabase.Supabase.instance.client;
          final token = ref.read(tokenStoreProvider).accessTokenSync;
          if (token != null) {
            final sessionJson = '''
            {
              "access_token": "$token",
              "refresh_token": "dummy-refresh-token",
              "expires_in": 86400,
              "token_type": "bearer",
              "user": {
                "id": "${widget.profile.userId}",
                "email": "${widget.profile.email}"
              }
            }
            ''';
            await supabaseClient.auth.recoverSession(sessionJson);
          }
          try {
            await supabaseClient.storage.from('avatar').remove([oldPath]);
          } catch (err) {
            debugPrint('Failed to delete previous avatar: $err');
          }
        }
      }
    } catch (e) {
      messenger.showSnackBar(
        SnackBar(content: Text('Failed to remove avatar: $e')),
      );
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate()) {
      setState(() => _autovalidate = true);
      return;
    }
    setState(() => _submitting = true);
    final messenger = ScaffoldMessenger.of(context);
    final router = GoRouter.of(context);
    try {
      await ref
          .read(profileRepositoryProvider)
          .updateMyProfile(
            UpdateProfilePayload(
              fullName: _fullName.text,
              phone: _phone.text,
              avatarUrl: _avatarUrl,
            ),
          );
      ref.invalidate(myProfileProvider);
      // Keep the dashboard greeting/avatar in sync (web: updateUserFields).
      ref
          .read(authControllerProvider.notifier)
          .updateUserFields(
            name: _fullName.text.trim(),
            avatarUrl: _avatarUrl,
            clearAvatar: _avatarUrl == null,
          );
      messenger.showSnackBar(
        const SnackBar(content: Text('Profile updated successfully')),
      );
      router.pop();
    } on AppException catch (e) {
      if (mounted) setState(() => _submitting = false);
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final hasAvatar = _avatarUrl != null && _avatarUrl!.isNotEmpty;

    return Scaffold(
      appBar: AppBar(title: const Text('Edit profile')),
      body: Form(
        key: _formKey,
        autovalidateMode: _autovalidate
            ? AutovalidateMode.onUserInteraction
            : AutovalidateMode.disabled,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(AppSpacing.lg, AppSpacing.xl, AppSpacing.lg, AppSpacing.huge),
          children: [
            // ── Avatar ──
            Center(
              child: Stack(
                children: [
                  GestureDetector(
                    onTap: _submitting ? null : _pickAvatar,
                    child: AppAvatar(
                      name: _fullName.text.isEmpty
                          ? widget.profile.fullName
                          : _fullName.text,
                      radius: 52,
                      imageUrl: _avatarUrl,
                    ),
                  ),
                  Positioned(
                    right: 0,
                    bottom: 0,
                    child: Material(
                      color: scheme.primary,
                      shape: const CircleBorder(),
                      elevation: 2,
                      child: InkWell(
                        customBorder: const CircleBorder(),
                        onTap: _submitting ? null : _pickAvatar,
                        child: Padding(
                          padding: const EdgeInsets.all(AppSpacing.sm),
                          child: Icon(
                            Icons.photo_camera_rounded,
                            size: 18,
                            color: scheme.onPrimary,
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            Center(
              child: TextButton.icon(
                onPressed: _submitting
                    ? null
                    : hasAvatar
                    ? _removeAvatar
                    : _pickAvatar,
                icon: Icon(
                  hasAvatar
                      ? Icons.delete_outline_rounded
                      : Icons.add_photo_alternate_outlined,
                  size: 18,
                ),
                label: Text(hasAvatar ? 'Remove photo' : 'Upload photo'),
              ),
            ),
            const SizedBox(height: 16),

            // ── Fields ──
            TextFormField(
              controller: _fullName,
              enabled: !_submitting,
              maxLength: 255,
              textCapitalization: TextCapitalization.words,
              decoration: const InputDecoration(
                labelText: 'Full name *',
                prefixIcon: Icon(Icons.person_outline_rounded),
                counterText: '',
              ),
              onChanged: (_) => setState(() {}), // live avatar initials
              validator: (v) => (v == null || v.trim().isEmpty)
                  ? 'Full name is required'
                  : null,
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _phone,
              enabled: !_submitting,
              maxLength: 15,
              keyboardType: TextInputType.phone,
              decoration: const InputDecoration(
                labelText: 'Phone number',
                hintText: 'e.g. 0912345678',
                prefixIcon: Icon(Icons.phone_outlined),
                counterText: '',
              ),
              validator: (v) {
                final value = (v ?? '').trim();
                if (value.isEmpty) return null;
                if (!RegExp(r'^[0-9+\-\s()]{6,15}$').hasMatch(value)) {
                  return 'Enter a valid phone number';
                }
                return null;
              },
            ),
            const SizedBox(height: 16),
            TextFormField(
              initialValue: widget.profile.email,
              enabled: false,
              decoration: const InputDecoration(
                labelText: 'Email address',
                prefixIcon: Icon(Icons.mail_outline_rounded),
                suffixIcon: Icon(Icons.lock_outline_rounded, size: 18),
              ),
            ),
            const SizedBox(height: 6),
            Text(
              'Your email is managed by your administrator and cannot be modified.',
              style: theme.textTheme.bodySmall?.copyWith(
                color: scheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 28),
            FilledButton(
              onPressed: _submitting ? null : _submit,
              style: FilledButton.styleFrom(
                minimumSize: const Size.fromHeight(52),
              ),
              child: _submitting
                  ? const SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(strokeWidth: 2.5),
                    )
                  : const Text('Save changes'),
            ),
          ],
        ),
      ),
    );
  }
}
