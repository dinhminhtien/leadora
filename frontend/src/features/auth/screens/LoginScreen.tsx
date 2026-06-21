"use client";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { KeyRound, Mail, AlertCircle, ArrowRight, Eye, EyeOff } from "lucide-react";

import { authService } from "@/services/auth_service";
import { useAuthStore } from "@/stores/auth_store";
import { supabaseAuthService } from "@/services/supabase_auth_service";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";

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
  const [showPassword, setShowPassword] = useState(false);

  const nextPath = searchParams.get("next") ?? ROUTE_PATHS.dashboard;
  const callbackError = searchParams.get("error");
  const setUser = useAuthStore((s) => s.setUser);

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
      const response = await authService.login({
        email: data.email,
        password: data.password,
      });
      if (response.data.accessToken) {
        localStorage.setItem("accessToken", response.data.accessToken);
      }
      setUser(response.data.user);
      router.push(nextPath);
    } catch (err: any) {
      const errorMsg = err.response?.data?.message || err.message || "Invalid email or password";
      setError(errorMsg);
    } finally {
      setIsLoading(false);
    }
  };

  const handleGoogleLogin = async () => {
    setIsGoogleLoading(true);
    setError(null);
    try {
      await supabaseAuthService.signInWithGoogle(nextPath);
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : "Google authentication failed";
      setError(errorMsg);
      setIsGoogleLoading(false);
    }
  };

  return (
    <div className="rounded-2xl border border-border bg-background/60 p-6.5 shadow-lg backdrop-blur-xl transition-all duration-300">
      <div className="flex flex-col space-y-1">
        <h2 className="text-lg font-bold tracking-tight text-foreground">
          Sign in to your account
        </h2>
        <p className="text-xs text-muted-foreground">
          Access your direct sales pipeline and activity boards.
        </p>
      </div>

      {(error || callbackError) && (
        <div className="mt-4 flex items-start gap-2.5 rounded-xl bg-danger/10 p-3 text-xs text-danger border border-danger/15">
          <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
          <p className="font-semibold leading-relaxed">
            {error ||
              (callbackError === "auth_callback_failed"
                ? "Failed to complete Google sign-in. Please try again."
                : "Authentication failed.")}
          </p>
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="mt-5 space-y-4">
        <div className="space-y-1.5">
          <label className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
            Email Address
          </label>
          <Input
            {...register("email")}
            type="email"
            disabled={isLoading || isGoogleLoading}
            placeholder="name@hotel.com"
            icon={<Mail className="h-4 w-4" />}
            error={errors.email?.message}
          />
        </div>

        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <label className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
              Password
            </label>
            <Link
              href={ROUTE_PATHS.forgotPassword}
              className="text-xs font-semibold text-primary hover:text-primary-hover transition-colors"
            >
              Forgot password?
            </Link>
          </div>
          <Input
            {...register("password")}
            type={showPassword ? "text" : "password"}
            disabled={isLoading || isGoogleLoading}
            placeholder="••••••••"
            icon={<KeyRound className="h-4 w-4" />}
            rightElement={
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="text-muted-foreground hover:text-foreground transition-colors p-1.5 focus:outline-none cursor-pointer flex items-center justify-center"
                title={showPassword ? "Hide password" : "Show password"}
              >
                {showPassword ? (
                  <EyeOff className="h-4 w-4" />
                ) : (
                  <Eye className="h-4 w-4" />
                )}
              </button>
            }
            error={errors.password?.message}
          />
        </div>

        <Button
          type="submit"
          disabled={isLoading || isGoogleLoading}
          isLoading={isLoading}
          className="w-full mt-2"
          rightIcon={<ArrowRight className="h-4 w-4" />}
        >
          Sign In
        </Button>
      </form>

      <div className="relative my-5 flex items-center justify-center">
        <div className="absolute inset-0 flex items-center">
          <div className="w-full border-t border-border"></div>
        </div>
        <span className="relative bg-background/60 px-3 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
          Or continue with
        </span>
      </div>

      <Button
        type="button"
        variant="outline"
        onClick={handleGoogleLogin}
        disabled={isLoading || isGoogleLoading}
        isLoading={isGoogleLoading}
        className="w-full"
        leftIcon={
          <svg className="h-4 w-4" viewBox="0 0 24 24">
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
        }
      >
        Google
      </Button>
    </div>
  );
}
