/**
 * Avatar source resolution.
 *
 * Small avatars are kept client-side under the `local-storage-avatar://`
 * pseudo-scheme rather than uploaded (see `ProfileScreen`'s 1 MB cap), so a
 * plain `<img src>` would render nothing without this indirection.
 *
 * **Why it lives here.** The identical function existed twice — exported from
 * `DashboardLayout` and re-declared privately inside `ProfileScreen`. Two copies
 * of a storage-key convention is one rename away from the topbar and the profile
 * page disagreeing about where a user's picture lives.
 */

/** Storage key for a user's locally-held avatar. One definition, one format. */
export function localAvatarKey(userId: string): string {
  return `local_avatar_${userId}`;
}

export const LOCAL_AVATAR_SCHEME = "local-storage-avatar://";

/**
 * Resolves a stored `avatarUrl` into something an `<img>` can display.
 * Returns `null` when there is no usable image, so callers fall back to initials.
 */
export function getAvatarSource(
  avatarUrl: string | null | undefined,
  userId: string | null | undefined,
): string | null {
  if (!avatarUrl) return null;

  if (avatarUrl.startsWith(LOCAL_AVATAR_SCHEME) && userId) {
    // Guarded because this runs during SSR too, where there is no localStorage.
    if (typeof window !== "undefined") {
      return localStorage.getItem(localAvatarKey(userId)) || null;
    }
    return null;
  }

  return avatarUrl;
}

/** Initials fallback, capped at two characters. */
export function initialsOf(name?: string | null): string {
  if (!name) return "?";
  return (
    name
      .trim()
      .split(/\s+/)
      .map((part) => part[0])
      .slice(0, 2)
      .join("")
      .toUpperCase() || "?"
  );
}
