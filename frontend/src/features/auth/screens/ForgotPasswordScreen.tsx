"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import Link from "next/link";
import { Mail, AlertCircle, CheckCircle2, ArrowLeft } from "lucide-react";

import { supabaseAuthService } from "@/services/supabase_auth_service";
import { ROUTE_PATHS } from "@/app/routes/route_paths";

const forgotPasswordSchema = z.object({
  email: z.string().email("Please enter a valid email address"),
});

type ForgotPasswordSchema = z.infer<typeof forgotPasswordSchema>;

export function ForgotPasswordScreen() {
  const [error, setError] = useState<string | null>(null);
  const [isSuccess, setIsSuccess] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ForgotPasswordSchema>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: {
      email: "",
    },
  });

  const onSubmit = async (data: ForgotPasswordSchema) => {
    setIsLoading(true);
    setError(null);
    setIsSuccess(false);

    try {
      await supabaseAuthService.resetPasswordForEmail(data.email);
      setIsSuccess(true);
    } catch (err: any) {
      setError(err?.message ?? "Failed to send password recovery email");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="rounded-2xl border border-zinc-200 bg-white p-8 shadow-xl transition-all dark:border-zinc-800 dark:bg-zinc-900">
      <div className="flex flex-col space-y-2 text-center">
        <h2 className="text-2xl font-bold tracking-tight text-zinc-900 dark:text-white">
          Reset password
        </h2>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          We will send a password reset link to your email address
        </p>
      </div>

      {isSuccess ? (
        <div className="mt-6 space-y-6">
          <div className="flex flex-col items-center gap-3 rounded-xl bg-teal-50/50 p-6 text-center dark:bg-teal-950/20">
            <CheckCircle2 className="h-12 w-12 text-teal-600 dark:text-teal-400" />
            <div className="space-y-1">
              <h3 className="text-sm font-semibold text-teal-900 dark:text-teal-300">
                Recovery email sent
              </h3>
              <p className="text-xs text-teal-700/80 dark:text-teal-400/80">
                Please check your inbox and spam folders for instructions to reset your password.
              </p>
            </div>
          </div>
          <Link
            href={ROUTE_PATHS.login}
            className="flex w-full items-center justify-center gap-2 rounded-xl border border-zinc-200 bg-white py-3 text-sm font-semibold text-zinc-700 transition-all hover:bg-zinc-50 active:scale-[0.98] dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-300 dark:hover:bg-zinc-900"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to Sign In
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
                Email Address
              </label>
              <div className="relative">
                <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-zinc-400">
                  <Mail className="h-4 w-4" />
                </span>
                <input
                  {...register("email")}
                  type="email"
                  disabled={isLoading}
                  placeholder="name@hotel.com"
                  className="w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 pl-10 pr-4 text-sm text-zinc-900 outline-none transition-all placeholder:text-zinc-400 focus:border-teal-500 focus:bg-white focus:ring-2 focus:ring-teal-500/20 dark:border-zinc-800 dark:bg-zinc-950 dark:text-white dark:focus:border-teal-500 dark:focus:bg-zinc-950"
                />
              </div>
              {errors.email && (
                <p className="text-xs text-red-600 dark:text-red-400">{errors.email.message}</p>
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
                "Send Reset Link"
              )}
            </button>

            <Link
              href={ROUTE_PATHS.login}
              className="flex w-full items-center justify-center gap-2 rounded-xl border border-zinc-200 bg-white py-3 text-sm font-semibold text-zinc-700 transition-all hover:bg-zinc-50 active:scale-[0.98] dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-300 dark:hover:bg-zinc-900"
            >
              <ArrowLeft className="h-4 w-4" />
              Back to Sign In
            </Link>
          </form>
        </>
      )}
    </div>
  );
}
