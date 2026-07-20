"use client";

import { useState, useRef, useMemo } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import {
  User,
  Mail,
  Phone,
  Shield,
  Calendar,
  Clock,
  Lock,
  Eye,
  EyeOff,
  CheckCircle,
  Camera,
  KeyRound,
  AlertTriangle,
  CheckCircle2,
} from "lucide-react";

import { useMyProfile, useUpdateProfile, useChangePassword } from "@/features/profile/hooks/use_profile";
import { useAuthStore } from "@/stores/auth_store";
import { toast } from "@/stores/toast_store";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";

// ── Validation schemas ────────────────────────────────────────────────────────

const updateProfileSchema = z.object({
  fullName: z
    .string()
    .min(1, "Full name is required")
    .max(255, "Full name must not exceed 255 characters"),
  phone: z
    .string()
    .max(15, "Phone number must not exceed 15 characters")
    .optional()
    .or(z.literal("")),
  avatarUrl: z
    .string()
    .max(500, "Avatar URL must not exceed 500 characters")
    .optional()
    .or(z.literal("")),
});

const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "Current password is required"),
    newPassword: z
      .string()
      .min(8, "Password must be at least 8 characters")
      .regex(/[A-Z]/, "Must contain at least one uppercase letter")
      .regex(/[a-z]/, "Must contain at least one lowercase letter")
      .regex(/[0-9]/, "Must contain at least one number")
      .regex(/[^A-Za-z0-9]/, "Must contain at least one special character"),
    confirmPassword: z.string().min(1, "Please confirm your new password"),
  })
  .refine((d) => d.newPassword === d.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

type UpdateProfileSchema = z.infer<typeof updateProfileSchema>;
type ChangePasswordSchema = z.infer<typeof changePasswordSchema>;

// ── Helpers ───────────────────────────────────────────────────────────────────

function getPasswordStrength(pw: string): { score: number; label: string; colorClass: string } {
  if (!pw) return { score: 0, label: "", colorClass: "" };
  let score = 0;
  if (pw.length >= 8) score++;
  if (pw.length >= 12) score++;
  if (/[A-Z]/.test(pw)) score++;
  if (/[a-z]/.test(pw)) score++;
  if (/[0-9]/.test(pw)) score++;
  if (/[^A-Za-z0-9]/.test(pw)) score++;
  
  if (score <= 2) return { score, label: "Weak", colorClass: "bg-rose-500" };
  if (score <= 4) return { score, label: "Medium", colorClass: "bg-amber-500" };
  return { score, label: "Strong", colorClass: "bg-emerald-500" };
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("en-US", {
    year: "numeric",
    month: "long",
    day: "numeric",
  });
}

function formatRoleName(role: string | null | undefined): string {
  if (!role) return "Staff";
  const map: Record<string, string> = {
    ADMIN: "Administrator",
    MANAGER: "Sales Manager",
    SALES: "Sales Staff",
    FRONT_OFFICE: "Front Office",
    STAFF: "Staff",
  };
  return map[role.toUpperCase()] ?? role;
}

function getStatusVariant(status: string): "success" | "warning" | "danger" {
  if (status === "ACTIVE") return "success";
  if (status === "INACTIVE") return "warning";
  return "danger";
}

function getAvatarSource(avatarUrl: string | null | undefined, userId: string | null | undefined): string | null {
  if (!avatarUrl) return null;
  if (avatarUrl.startsWith("local-storage-avatar://") && userId) {
    if (typeof window !== "undefined") {
      return localStorage.getItem(`local_avatar_${userId}`) || null;
    }
  }
  return avatarUrl;
}

// ── Component ─────────────────────────────────────────────────────────────────

export function ProfileScreen() {
  const { data: profile, isLoading } = useMyProfile();
  const updateProfileMutation = useUpdateProfile();
  const changePasswordMutation = useChangePassword();
  const { updateUserFields } = useAuthStore();

  const [showCurrentPw, setShowCurrentPw] = useState(false);
  const [showNewPw, setShowNewPw] = useState(false);
  const [showConfirmPw, setShowConfirmPw] = useState(false);

  const [shakeFields, setShakeFields] = useState(false);
  const [isCapsLockOn, setIsCapsLockOn] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const checkCapsLock = (e: React.KeyboardEvent) => {
    if (e.getModifierState) {
      setIsCapsLockOn(e.getModifierState("CapsLock"));
    }
  };

  const onPasswordError = () => {
    setShakeFields(true);
    setTimeout(() => setShakeFields(false), 400);
  };

  // Edit Profile form — pre-filled from fetched profile
  const {
    register: registerProfile,
    handleSubmit: handleProfileSubmit,
    formState: { errors: profileErrors, isDirty: isProfileDirty },
    reset: resetProfile,
  } = useForm<UpdateProfileSchema>({
    resolver: zodResolver(updateProfileSchema),
    values: {
      fullName: profile?.fullName ?? "",
      phone: profile?.phone ?? "",
      avatarUrl: profile?.avatarUrl ?? "",
    },
  });

  // Change Password form
  const {
    register: registerPassword,
    handleSubmit: handlePasswordSubmit,
    formState: { errors: passwordErrors },
    watch: watchPassword,
    reset: resetPassword,
  } = useForm<ChangePasswordSchema>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: "", newPassword: "", confirmPassword: "" },
  });

  const newPasswordValue = watchPassword("newPassword") ?? "";
  const passwordStrength = getPasswordStrength(newPasswordValue);

  // ── Handlers ─────────────────────────────────────────────────────────────────

  const onProfileSubmit = async (data: UpdateProfileSchema) => {
    try {
      await updateProfileMutation.mutateAsync({
        fullName: data.fullName,
        phone: data.phone || null,
        avatarUrl: data.avatarUrl || null,
      });

      updateUserFields({
        name: data.fullName,
        avatarUrl: data.avatarUrl || null,
      });

      toast.success("Profile updated successfully.");
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        "Failed to update profile. Please try again.";
      toast.error(message);
    }
  };

  const onPasswordSubmit = async (data: ChangePasswordSchema) => {
    try {
      await changePasswordMutation.mutateAsync({
        currentPassword: data.currentPassword,
        newPassword: data.newPassword,
      });
      toast.success("Password changed successfully.");
      resetPassword();
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        "Failed to change password. Please try again.";
      toast.error(message);
      onPasswordError();
    }
  };

  const handleAvatarClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 1024 * 1024) {
      toast.error("Avatar image size must be under 1MB.");
      return;
    }

    const reader = new FileReader();
    reader.onload = async () => {
      const base64Data = reader.result as string;
      if (profile?.userId) {
        localStorage.setItem(`local_avatar_${profile.userId}`, base64Data);
        const placeholderUrl = `local-storage-avatar://${profile.userId}`;
        
        try {
          await updateProfileMutation.mutateAsync({
            fullName: profile.fullName,
            phone: profile.phone,
            avatarUrl: placeholderUrl,
          });

          updateUserFields({
            avatarUrl: placeholderUrl,
          });

          toast.success("Avatar updated successfully.");
        } catch (err) {
          toast.error("Failed to update avatar image.");
        }
      }
    };
    reader.readAsDataURL(file);
  };

  // Avatar initials
  const avatarInitials = profile?.fullName
    ? profile.fullName
        .split(" ")
        .map((n) => n[0])
        .slice(0, 2)
        .join("")
        .toUpperCase()
    : "??";

  const avatarSource = useMemo(() => {
    return getAvatarSource(profile?.avatarUrl, profile?.userId);
  }, [profile?.avatarUrl, profile?.userId]);

  // ── Loading skeleton ──────────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <div className="mx-auto max-w-6xl">
        <div className="h-20 w-full bg-muted rounded-xl animate-pulse mb-8" />
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 max-w-6xl animate-pulse">
          <div className="lg:col-span-1 h-96 rounded-2xl bg-muted" />
          <div className="lg:col-span-2 flex flex-col gap-6">
            <div className="h-80 rounded-xl bg-muted" />
            <div className="h-64 rounded-xl bg-muted" />
          </div>
        </div>
      </div>
    );
  }

  // ── Render ────────────────────────────────────────────────────────────────────

  return (
    <div className="mx-auto max-w-6xl">
      <style>{`
        @keyframes shake {
          0%, 100% { transform: translateX(0); }
          10%, 30%, 50%, 70%, 90% { transform: translateX(-4px); }
          20%, 40%, 60%, 80% { transform: translateX(4px); }
        }
        .animate-shake {
          animation: shake 0.4s ease-in-out;
        }
      `}</style>
      
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-xl font-bold text-slate-800">Profile Settings</h1>
        <p className="text-xs text-slate-400 mt-0.5">
          Manage your account information and security settings
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
        
        {/* ── LEFT COLUMN: Profile Summary Card ───────────────────────────────── */}
        <div className="lg:col-span-1 flex flex-col gap-6">
          <Card className="overflow-hidden shadow-md border border-zinc-200 dark:border-zinc-850">
            {/* Gradient Header */}
            <div className="h-28 bg-linear-to-r from-primary/90 via-primary to-primary-dark/80 relative" />
            
            <CardContent className="px-5 pb-6">
              {/* Avatar Area */}
              <div className="flex flex-col items-center -mt-14 relative z-10">
                <div
                  onClick={handleAvatarClick}
                  className="group relative size-28 rounded-full border-4 border-background bg-zinc-150 dark:bg-zinc-850 shadow-md cursor-pointer overflow-hidden transition-all duration-300 hover:scale-105 hover:border-primary/20"
                >
                  {avatarSource ? (
                    <img
                      src={avatarSource}
                      alt={profile?.fullName || "Avatar"}
                      className="size-full object-cover"
                    />
                  ) : (
                    <div className="flex size-full items-center justify-center bg-primary text-white text-3xl font-bold">
                      {avatarInitials}
                    </div>
                  )}
                  {/* Hover overlay with camera icon */}
                  <div className="absolute inset-0 bg-black/45 opacity-0 group-hover:opacity-100 flex flex-col items-center justify-center text-white transition-opacity duration-200">
                    <Camera className="size-5 mb-0.5" />
                    <span className="text-[10px] font-semibold uppercase tracking-wider">Upload</span>
                  </div>
                </div>
                {/* Hidden File Input */}
                <input
                  type="file"
                  ref={fileInputRef}
                  onChange={handleFileChange}
                  accept="image/*"
                  className="hidden"
                />
                
                {/* User Title & Badges */}
                <h2 className="mt-4 text-lg font-bold text-foreground truncate max-w-full text-center">
                  {profile?.fullName}
                </h2>
                
                <div className="flex flex-wrap items-center justify-center gap-1.5 mt-2.5">
                  <Badge variant="primary" className="text-[9px] px-2 py-0.5 font-bold uppercase tracking-wider scale-95">
                    {formatRoleName(profile?.roleName)}
                  </Badge>
                  {profile?.status && (
                    <Badge variant={getStatusVariant(profile.status)} className="text-[9px] px-2 py-0.5 font-bold uppercase tracking-wider scale-95">
                      {profile.status}
                    </Badge>
                  )}
                </div>
              </div>
              
              <div className="h-px bg-zinc-200/60 dark:bg-zinc-850/50 my-5" />
              
              {/* Metadata detail list */}
              <dl className="space-y-3.5">
                {[
                  { Icon: Mail, label: "Email Address", value: profile?.email },
                  { Icon: Phone, label: "Phone Number", value: profile?.phone || "Not provided", isSoft: !profile?.phone },
                  { Icon: Shield, label: "Role Name", value: formatRoleName(profile?.roleName) },
                  { Icon: Shield, label: "Account Status", value: profile?.status || "Unknown" },
                  { Icon: Calendar, label: "Member Since", value: formatDate(profile?.createdAt) },
                  { Icon: Clock, label: "Last Login", value: formatDate(profile?.lastLoginAt) },
                ].map(({ Icon, label, value, isSoft }) => (
                  <div key={label} className="flex items-start gap-3">
                    <div className="mt-0.5 flex size-6.5 shrink-0 items-center justify-center rounded-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200/40 dark:border-zinc-800/40">
                      <Icon className="size-3.5 text-zinc-500 dark:text-zinc-400" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <dt className="text-[9px] font-bold uppercase tracking-wider text-muted-foreground/80">
                        {label}
                      </dt>
                      <dd className={`mt-0.5 text-xs font-semibold truncate ${isSoft ? "text-muted-foreground/60 italic font-normal" : "text-foreground"}`}>
                        {value}
                      </dd>
                    </div>
                  </div>
                ))}
              </dl>
            </CardContent>
          </Card>
        </div>

        {/* ── RIGHT COLUMN: Forms Section ─────────────────────────────────────── */}
        <div className="lg:col-span-2 flex flex-col gap-6">
          
          {/* Profile form */}
          <Card className="shadow-md border border-zinc-200 dark:border-zinc-850">
            <CardHeader>
              <div>
                <CardTitle>Profile Information</CardTitle>
                <CardDescription className="mt-1">
                  Update your contact details and full name.
                </CardDescription>
              </div>
            </CardHeader>
            <CardContent className="px-5 pb-6">
              <form
                onSubmit={handleProfileSubmit(onProfileSubmit)}
                className="flex flex-col gap-5"
              >
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                  <div className="flex flex-col gap-1.5">
                    <label className="text-xs font-semibold text-foreground">
                      Full Name <span className="text-danger">*</span>
                    </label>
                    <Input
                      {...registerProfile("fullName")}
                      placeholder="Enter your full name"
                      icon={<User className="size-3.5 text-zinc-400" />}
                      error={profileErrors.fullName?.message}
                      className="bg-zinc-50/50 focus:bg-background transition-all duration-200"
                    />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <label className="text-xs font-semibold text-foreground">
                      Phone Number
                    </label>
                    <Input
                      {...registerProfile("phone")}
                      phoneOnly
                      placeholder="e.g. 0912345678"
                      icon={<Phone className="size-3.5 text-zinc-400" />}
                      error={profileErrors.phone?.message}
                      className="bg-zinc-50/50 focus:bg-background transition-all duration-200"
                    />
                  </div>
                </div>

                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-semibold text-foreground flex items-center gap-1.5">
                    Email Address
                    <Lock className="size-3 text-muted-foreground/60" />
                  </label>
                  <Input
                    value={profile?.email || ""}
                    disabled
                    icon={<Mail className="size-3.5 text-zinc-400" />}
                    className="bg-zinc-100/60 dark:bg-zinc-900/60 text-muted-foreground border-zinc-250 dark:border-zinc-800/80 cursor-not-allowed select-none shadow-none"
                  />
                  <p className="text-[10px] text-muted-foreground/70 pl-1 mt-0.5">
                    Your email is managed by your administrator and cannot be modified.
                  </p>
                </div>

                <div className="flex justify-end gap-3 pt-3 border-t border-zinc-150 dark:border-zinc-850">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => resetProfile()}
                    disabled={!isProfileDirty || updateProfileMutation.isPending}
                    className="rounded-lg text-xs"
                  >
                    Reset
                  </Button>
                  <Button
                    type="submit"
                    variant="primary"
                    isLoading={updateProfileMutation.isPending}
                    disabled={!isProfileDirty}
                    leftIcon={<CheckCircle className="size-3.5" />}
                    className="rounded-lg text-xs font-bold"
                  >
                    Save Changes
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>

          {/* Change Password form */}
          <Card className="shadow-md border border-zinc-200 dark:border-zinc-850">
            <CardHeader>
              <div>
                <CardTitle>Change Password</CardTitle>
                <CardDescription className="mt-1">
                  Ensure your account is using a secure, random password.
                </CardDescription>
              </div>
            </CardHeader>
            <CardContent className="px-5 pb-6">
              <form
                onSubmit={handlePasswordSubmit(onPasswordSubmit, onPasswordError)}
                className={`flex flex-col gap-5 ${shakeFields ? "animate-shake" : ""}`}
              >
                {/* 3 password fields row */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
                  <div className="flex flex-col gap-1.5">
                    <label className="text-xs font-semibold text-foreground">
                      Current Password <span className="text-danger">*</span>
                    </label>
                    <Input
                      {...registerPassword("currentPassword")}
                      type={showCurrentPw ? "text" : "password"}
                      placeholder="••••••••"
                      icon={<Lock className="size-3.5 text-zinc-400" />}
                      onKeyDown={checkCapsLock}
                      onKeyUp={checkCapsLock}
                      rightElement={
                        <button
                          type="button"
                          onClick={() => setShowCurrentPw((v) => !v)}
                          className="text-muted-foreground hover:text-foreground transition cursor-pointer"
                        >
                          {showCurrentPw ? (
                            <EyeOff className="size-3.5" />
                          ) : (
                            <Eye className="size-3.5" />
                          )}
                        </button>
                      }
                      error={passwordErrors.currentPassword?.message}
                      className="bg-zinc-50/50 focus:bg-background transition-all duration-200"
                    />
                  </div>

                  <div className="flex flex-col gap-1.5">
                    <label className="text-xs font-semibold text-foreground">
                      New Password <span className="text-danger">*</span>
                    </label>
                    <Input
                      {...registerPassword("newPassword")}
                      type={showNewPw ? "text" : "password"}
                      placeholder="••••••••"
                      icon={<KeyRound className="size-3.5 text-zinc-400" />}
                      onKeyDown={checkCapsLock}
                      onKeyUp={checkCapsLock}
                      rightElement={
                        <button
                          type="button"
                          onClick={() => setShowNewPw((v) => !v)}
                          className="text-muted-foreground hover:text-foreground transition cursor-pointer"
                        >
                          {showNewPw ? (
                            <EyeOff className="size-3.5" />
                          ) : (
                            <Eye className="size-3.5" />
                          )}
                        </button>
                      }
                      error={passwordErrors.newPassword?.message}
                      className="bg-zinc-50/50 focus:bg-background transition-all duration-200"
                    />
                  </div>

                  <div className="flex flex-col gap-1.5">
                    <label className="text-xs font-semibold text-foreground">
                      Confirm Password <span className="text-danger">*</span>
                    </label>
                    <Input
                      {...registerPassword("confirmPassword")}
                      type={showConfirmPw ? "text" : "password"}
                      placeholder="••••••••"
                      icon={<Lock className="size-3.5 text-zinc-400" />}
                      onKeyDown={checkCapsLock}
                      onKeyUp={checkCapsLock}
                      rightElement={
                        <button
                          type="button"
                          onClick={() => setShowConfirmPw((v) => !v)}
                          className="text-muted-foreground hover:text-foreground transition cursor-pointer"
                        >
                          {showConfirmPw ? (
                            <EyeOff className="size-3.5" />
                          ) : (
                            <Eye className="size-3.5" />
                          )}
                        </button>
                      }
                      error={passwordErrors.confirmPassword?.message}
                      className="bg-zinc-50/50 focus:bg-background transition-all duration-200"
                    />
                  </div>
                </div>

                {/* Caps Lock warning indicator */}
                {isCapsLockOn && (
                  <div className="flex items-center gap-1.5 text-xs text-amber-600 dark:text-amber-400 bg-amber-50 dark:bg-amber-950/20 border border-amber-200/50 dark:border-amber-900/30 px-3 py-1.5 rounded-lg transition-all duration-200">
                    <AlertTriangle className="size-3.5 animate-pulse shrink-0 text-amber-500" />
                    <span className="font-semibold">Caps Lock is active</span>
                  </div>
                )}

                {/* Strength meter + requirement checklist (compact) */}
                {newPasswordValue.length > 0 && (
                  <div className="rounded-lg border border-zinc-200/60 dark:border-zinc-850 bg-zinc-50/30 dark:bg-zinc-900/10 px-3 py-2.5 space-y-2.5">
                    {/* Strength row */}
                    <div className="flex items-center gap-3">
                      <div className="flex gap-1 flex-1">
                        {[1, 2, 3].map((i) => (
                          <div
                            key={i}
                            className={`h-1.5 flex-1 rounded-full transition-all duration-300 ${
                              passwordStrength.label === "Weak" && i === 1
                                ? "bg-rose-500"
                                : passwordStrength.label === "Medium" && i <= 2
                                  ? "bg-amber-500"
                                  : passwordStrength.label === "Strong"
                                    ? "bg-emerald-500"
                                    : "bg-zinc-150 dark:bg-zinc-850"
                            }`}
                          />
                        ))}
                      </div>
                      <span
                        className={`text-[11px] font-bold shrink-0 ${
                          passwordStrength.label === "Weak"
                            ? "text-rose-500"
                            : passwordStrength.label === "Medium"
                              ? "text-amber-500"
                              : "text-emerald-500"
                        }`}
                      >
                        {passwordStrength.label}
                      </span>
                    </div>

                    {/* Requirements — inline wrap */}
                    <div className="flex flex-wrap gap-x-3 gap-y-1">
                      {[
                        { label: "8+ chars", met: newPasswordValue.length >= 8 },
                        { label: "Uppercase", met: /[A-Z]/.test(newPasswordValue) },
                        { label: "Lowercase", met: /[a-z]/.test(newPasswordValue) },
                        { label: "Number", met: /[0-9]/.test(newPasswordValue) },
                        { label: "Special", met: /[^A-Za-z0-9]/.test(newPasswordValue) },
                      ].map(({ label, met }) => (
                        <span
                          key={label}
                          className={`flex items-center gap-1 text-[11px] font-medium transition-colors ${
                            met ? "text-emerald-600 dark:text-emerald-400" : "text-muted-foreground"
                          }`}
                        >
                          {met ? (
                            <CheckCircle2 className="size-3 shrink-0" />
                          ) : (
                            <span className="size-1 rounded-full bg-zinc-300 dark:bg-zinc-700 shrink-0" />
                          )}
                          {label}
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                <div className="flex justify-end pt-3 border-t border-zinc-150 dark:border-zinc-850">
                  <Button
                    type="submit"
                    variant="primary"
                    isLoading={changePasswordMutation.isPending}
                    leftIcon={<Shield className="size-3.5" />}
                    className="rounded-lg text-xs font-bold"
                  >
                    Change Password
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>

        </div>
      </div>
    </div>
  );
}
