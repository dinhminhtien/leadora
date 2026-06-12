"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import Link from "next/link";
import { KeyRound, AlertCircle, CheckCircle2, ArrowRight } from "lucide-react";

import { supabaseAuthService } from "@/services/supabase_auth_service";
import { ROUTE_PATHS } from "@/app/routes/route_paths";

const resetPasswordSchema = z
  .object({
    password: z.string().min(6, "Password must be at least 6 characters"),
    confirmPassword: z.string(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

type ResetPasswordSchema = z.infer<typeof resetPasswordSchema>;

export function ResetPasswordScreen() {
  const [error, setError] = useState<string | null>(null);
  const [isSuccess, setIsSuccess] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ResetPasswordSchema>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: {
      password: "",
      confirmPassword: "",
    },
  });

  const onSubmit = async (data: ResetPasswordSchema) => {
    setIsLoading(true);
    setError(null);
    setIsSuccess(false);

    try {
      await supabaseAuthService.updatePassword(data.password);
      setIsSuccess(true);
    } catch (err: any) {
      setError(err?.message ?? "Failed to update password. Reset token might have expired.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="rounded-2xl border border-zinc-200 bg-white p-8 shadow-xl transition-all dark:border-zinc-800 dark:bg-zinc-900">
      <div className="flex flex-col space-y-2 text-center">
        <h2 className="text-2xl font-bold tracking-tight text-zinc-900 dark:text-white">
          Set new password
        </h2>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          Enter your new password below to secure your account
        </p>
      </div>

      {isSuccess ? (
        <div className="mt-6 space-y-6">
          <div className="flex flex-col items-center gap-3 rounded-xl bg-teal-50/50 p-6 text-center dark:bg-teal-950/20">
            <CheckCircle2 className="h-12 w-12 text-teal-600 dark:text-teal-400" />
            <div className="space-y-1">
              <h3 className="text-sm font-semibold text-teal-900 dark:text-teal-300">
                Password updated
              </h3>
              <p className="text-xs text-teal-700/80 dark:text-teal-400/80">
                Your password has been successfully updated. You can now use your new password to sign in.
              </p>
            </div>
          </div>
          <Link
            href={ROUTE_PATHS.login}
            className="flex w-full items-center justify-center gap-2 rounded-xl bg-teal-600 py-3 text-sm font-semibold text-white transition-all hover:bg-teal-500 active:scale-[0.98]"
          >
            Go to Sign In
            <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
      ) : (
        <>
          {error && (
            <div className="mt-4 flex items-center gap-2 rounded-lg bg-red-50 p-3 text-sm text-red-600 dark:bg-red-950/30 dark:text-red-400">
              <AlertCircle className="h-4 w-4 shrink-0" />
              <p className="font-medium">{error}</p>
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="mt-6 space-y-4">
            <div className="space-y-1">
              <label className="text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                New Password
              </label>
              <div className="relative">
                <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-zinc-400">
                  <KeyRound className="h-4 w-4" />
                </span>
                <input
                  {...register("password")}
                  type="password"
                  disabled={isLoading}
                  placeholder="••••••••"
                  className="w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 pl-10 pr-4 text-sm text-zinc-900 outline-none transition-all placeholder:text-zinc-400 focus:border-teal-500 focus:bg-white focus:ring-2 focus:ring-teal-500/20 dark:border-zinc-800 dark:bg-zinc-950 dark:text-white dark:focus:border-teal-500 dark:focus:bg-zinc-950"
                />
              </div>
              {errors.password && (
                <p className="text-xs text-red-600 dark:text-red-400">{errors.password.message}</p>
              )}
            </div>

            <div className="space-y-1">
              <label className="text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                Confirm New Password
              </label>
              <div className="relative">
                <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-zinc-400">
                  <KeyRound className="h-4 w-4" />
                </span>
                <input
                  {...register("confirmPassword")}
                  type="password"
                  disabled={isLoading}
                  placeholder="••••••••"
                  className="w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 pl-10 pr-4 text-sm text-zinc-900 outline-none transition-all placeholder:text-zinc-400 focus:border-teal-500 focus:bg-white focus:ring-2 focus:ring-teal-500/20 dark:border-zinc-800 dark:bg-zinc-950 dark:text-white dark:focus:border-teal-500 dark:focus:bg-zinc-950"
                />
              </div>
              {errors.confirmPassword && (
                <p className="text-xs text-red-600 dark:text-red-400">{errors.confirmPassword.message}</p>
              )}
            </div>

            <button
              type="submit"
              disabled={isLoading}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-teal-600 py-3 text-sm font-semibold text-white transition-all hover:bg-teal-500 active:scale-[0.98] disabled:pointer-events-none disabled:opacity-50"
            >
              {isLoading ? (
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
              ) : (
                "Update Password"
              )}
            </button>
          </form>
        </>
      )}
    </div>
  );
}
