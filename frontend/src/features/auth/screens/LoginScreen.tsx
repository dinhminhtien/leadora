"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { KeyRound, Mail, AlertCircle, ArrowRight } from "lucide-react";

import { supabaseAuthService } from "@/services/supabase_auth_service";
import { ROUTE_PATHS } from "@/app/routes/route_paths";

const loginSchema = z.object({
  email: z.string().email("Please enter a valid email address"),
  password: z.string().min(6, "Password must be at least 6 characters"),
});

type LoginSchema = z.infer<typeof loginSchema>;

export function LoginScreen() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isGoogleLoading, setIsGoogleLoading] = useState(false);

  const nextPath = searchParams.get("next") ?? ROUTE_PATHS.dashboard;
  const callbackError = searchParams.get("error");

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginSchema>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: "",
      password: "",
    },
  });

  const onSubmit = async (data: LoginSchema) => {
    setIsLoading(true);
    setError(null);

    try {
      await supabaseAuthService.signInWithPassword({
        email: data.email,
        password: data.password,
      });
      router.push(nextPath);
    } catch (err: any) {
      setError(err?.message ?? "Invalid email or password");
    } finally {
      setIsLoading(false);
    }
  };

  const handleGoogleLogin = async () => {
    setIsGoogleLoading(true);
    setError(null);
    try {
      await supabaseAuthService.signInWithGoogle(nextPath);
    } catch (err: any) {
      setError(err?.message ?? "Google authentication failed");
      setIsGoogleLoading(false);
    }
  };

  return (
    <div className="rounded-2xl border border-zinc-200 bg-white p-8 shadow-xl transition-all dark:border-zinc-800 dark:bg-zinc-900">
      <div className="flex flex-col space-y-2 text-center">
        <h2 className="text-2xl font-bold tracking-tight text-zinc-900 dark:text-white">
          Welcome back
        </h2>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          Enter your details to sign in to your Leadora account
        </p>
      </div>

      {(error || callbackError) && (
        <div className="mt-4 flex items-center gap-2 rounded-lg bg-red-50 p-3 text-sm text-red-600 dark:bg-red-950/30 dark:text-red-400">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <p className="font-medium">
            {error ||
              (callbackError === "auth_callback_failed"
                ? "Failed to complete Google sign-in. Please try again."
                : "Authentication failed.")}
          </p>
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
              disabled={isLoading || isGoogleLoading}
              placeholder="name@hotel.com"
              className="w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 pl-10 pr-4 text-sm text-zinc-900 outline-none transition-all placeholder:text-zinc-400 focus:border-teal-500 focus:bg-white focus:ring-2 focus:ring-teal-500/20 dark:border-zinc-800 dark:bg-zinc-950 dark:text-white dark:focus:border-teal-500 dark:focus:bg-zinc-950"
            />
          </div>
          {errors.email && (
            <p className="text-xs text-red-600 dark:text-red-400">{errors.email.message}</p>
          )}
        </div>

        <div className="space-y-1">
          <div className="flex items-center justify-between">
            <label className="text-xs font-semibold text-zinc-700 dark:text-zinc-300">
              Password
            </label>
            <Link
              href={ROUTE_PATHS.forgotPassword}
              className="text-xs font-medium text-teal-600 hover:text-teal-500 hover:underline dark:text-teal-400 dark:hover:text-teal-300"
            >
              Forgot password?
            </Link>
          </div>
          <div className="relative">
            <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-zinc-400">
              <KeyRound className="h-4 w-4" />
            </span>
            <input
              {...register("password")}
              type="password"
              disabled={isLoading || isGoogleLoading}
              placeholder="••••••••"
              className="w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 pl-10 pr-4 text-sm text-zinc-900 outline-none transition-all placeholder:text-zinc-400 focus:border-teal-500 focus:bg-white focus:ring-2 focus:ring-teal-500/20 dark:border-zinc-800 dark:bg-zinc-950 dark:text-white dark:focus:border-teal-500 dark:focus:bg-zinc-950"
            />
          </div>
          {errors.password && (
            <p className="text-xs text-red-600 dark:text-red-400">{errors.password.message}</p>
          )}
        </div>

        <button
          type="submit"
          disabled={isLoading || isGoogleLoading}
          className="flex w-full items-center justify-center gap-2 rounded-xl bg-teal-600 py-3 text-sm font-semibold text-white transition-all hover:bg-teal-500 active:scale-[0.98] disabled:pointer-events-none disabled:opacity-50"
        >
          {isLoading ? (
            <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
          ) : (
            <>
              Sign In
              <ArrowRight className="h-4 w-4" />
            </>
          )}
        </button>
      </form>

      <div className="relative my-6 flex items-center justify-center">
        <div className="absolute inset-0 flex items-center">
          <div className="w-full border-t border-zinc-200 dark:border-zinc-800"></div>
        </div>
        <span className="relative bg-white px-3 text-xs text-zinc-500 dark:bg-zinc-900 dark:text-zinc-400">
          Or continue with
        </span>
      </div>

      <button
        type="button"
        onClick={handleGoogleLogin}
        disabled={isLoading || isGoogleLoading}
        className="flex w-full items-center justify-center gap-3 rounded-xl border border-zinc-200 bg-white py-3 text-sm font-semibold text-zinc-700 transition-all hover:bg-zinc-50 active:scale-[0.98] disabled:pointer-events-none disabled:opacity-50 dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-300 dark:hover:bg-zinc-900"
      >
        {isGoogleLoading ? (
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-zinc-700 border-t-transparent dark:border-zinc-300" />
        ) : (
          <>
            <svg className="h-5 w-5" viewBox="0 0 24 24">
              <path
                fill="currentColor"
                d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
              />
              <path
                fill="currentColor"
                d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
              />
              <path
                fill="currentColor"
                d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
              />
              <path
                fill="currentColor"
                d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
              />
            </svg>
            Google
          </>
        )}
      </button>
    </div>
  );
}
