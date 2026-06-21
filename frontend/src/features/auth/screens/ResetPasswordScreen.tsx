"use client";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import Link from "next/link";
import { KeyRound, AlertCircle, CheckCircle2, ArrowRight, Eye, EyeOff, Check, X } from "lucide-react";

import { useSearchParams } from "next/navigation";
import { apiClient } from "@/services/api_client";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";

const resetPasswordSchema = z
  .object({
    password: z
      .string()
      .min(6, "Password must be at least 6 characters")
      .regex(/[A-Z]/, "Password must contain at least one uppercase letter")
      .regex(/[a-z]/, "Password must contain at least one lowercase letter")
      .regex(/\d/, "Password must contain at least one digit")
      .regex(/[^A-Za-z\d\s]/, "Password must contain at least one symbol"),
    confirmPassword: z.string(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

type ResetPasswordSchema = z.infer<typeof resetPasswordSchema>;

export function ResetPasswordScreen() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token") || "";
  const [error, setError] = useState<string | null>(null);
  const [isSuccess, setIsSuccess] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<ResetPasswordSchema>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: {
      password: "",
      confirmPassword: "",
    },
  });

  const passwordVal = watch("password") || "";
  const confirmPasswordVal = watch("confirmPassword") || "";

  const requirements = [
    { label: "6+ characters", checked: passwordVal.length >= 6 },
    { label: "Uppercase (A-Z)", checked: /[A-Z]/.test(passwordVal) },
    { label: "Lowercase (a-z)", checked: /[a-z]/.test(passwordVal) },
    { label: "Digit (0-9)", checked: /\d/.test(passwordVal) },
    { label: "Special character", checked: /[^A-Za-z\d\s]/.test(passwordVal) },
  ];

  const onSubmit = async (data: ResetPasswordSchema) => {
    setIsLoading(true);
    setError(null);
    setIsSuccess(false);

    if (!token) {
      setError("Reset token is missing from the link. Please make sure you copied the entire URL.");
      setIsLoading(false);
      return;
    }

    try {
      await apiClient.post("/auth/reset-password", {
        token: token,
        password: data.password,
      });
      setIsSuccess(true);
    } catch (err: any) {
      const errorMsg = err.response?.data?.message || err.message || "Failed to update password. Reset token might have expired.";
      setError(errorMsg);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="rounded-2xl border border-border bg-background/60 p-6.5 shadow-lg backdrop-blur-xl transition-all duration-300">
      <div className="flex flex-col space-y-1">
        <h2 className="text-lg font-bold tracking-tight text-foreground">
          Set new password
        </h2>
        <p className="text-xs text-muted-foreground">
          Enter your new password below to secure your account.
        </p>
      </div>

      {isSuccess ? (
        <div className="mt-5 space-y-5">
          <div className="flex flex-col items-center gap-3 rounded-xl bg-success/10 p-5 text-center border border-success/15">
            <CheckCircle2 className="h-9 w-9 text-success" />
            <div className="space-y-1">
              <h3 className="text-sm font-bold text-foreground">
                Password updated
              </h3>
              <p className="text-xs text-muted-foreground leading-relaxed">
                Your password has been successfully updated. You can now use your new password to sign in.
              </p>
            </div>
          </div>
          <Link href={ROUTE_PATHS.login} className="block w-full">
            <Button className="w-full" rightIcon={<ArrowRight className="h-4 w-4" />}>
              Go to Sign In
            </Button>
          </Link>
        </div>
      ) : (
        <>
          {error && (
            <div className="mt-4 flex items-start gap-2.5 rounded-xl bg-danger/10 p-3 text-xs text-danger border border-danger/15">
              <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
              <p className="font-semibold leading-relaxed">{error}</p>
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="mt-5 space-y-4">
            <div className="space-y-1.5">
              <label className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
                New Password
              </label>
              <Input
                {...register("password")}
                type={showPassword ? "text" : "password"}
                disabled={isLoading}
                placeholder="••••••••"
                icon={<KeyRound className="h-4 w-4" />}
                error={errors.password?.message}
                rightElement={
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="text-muted-foreground hover:text-foreground transition-colors p-1"
                    disabled={isLoading}
                  >
                    {showPassword ? (
                      <EyeOff className="h-4 w-4" />
                    ) : (
                      <Eye className="h-4 w-4" />
                    )}
                  </button>
                }
              />
            </div>

            {/* Dynamic Real-time checklist as a symmetric OCD-friendly panel */}
            <div className="rounded-xl border border-border bg-input/20 p-3.5 space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
                  Password Security
                </span>
                <span className="text-[10px] font-bold text-muted-foreground">
                  {requirements.filter((r) => r.checked).length} / 5 passed
                </span>
              </div>

              {/* Symmetric 5-segment progress bar */}
              <div className="grid grid-cols-5 gap-1.5">
                {requirements.map((req, i) => {
                  const passedCount = requirements.filter((r) => r.checked).length;
                  const isFilled = i < passedCount;
                  let barClass = "bg-muted-foreground/20";
                  if (isFilled) {
                    if (passedCount <= 2) barClass = "bg-danger";
                    else if (passedCount <= 4) barClass = "bg-warning";
                    else barClass = "bg-success";
                  }
                  return (
                    <div
                      key={i}
                      className={`h-1.5 rounded-full transition-all duration-300 ${barClass}`}
                    />
                  );
                })}
              </div>

              {/* Perfectly aligned requirements checklist */}
              <div className="space-y-2 pt-1">
                {requirements.map((req, i) => (
                  <div
                    key={i}
                    className="flex items-center justify-between text-xs py-1 border-b border-border/30 last:border-none"
                  >
                    <span
                      className={`transition-colors duration-300 ${req.checked ? "text-success font-semibold" : "text-muted-foreground"
                        }`}
                    >
                      {req.label}
                    </span>
                    {req.checked ? (
                      <CheckCircle2 className="h-4 w-4 text-success shrink-0" />
                    ) : (
                      <div className="h-4 w-4 rounded-full border-2 border-border bg-background shrink-0" />
                    )}
                  </div>
                ))}
              </div>
            </div>

            <div className="space-y-1.5">
              <label className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
                Confirm New Password
              </label>
              <Input
                {...register("confirmPassword")}
                type={showConfirmPassword ? "text" : "password"}
                disabled={isLoading}
                placeholder="••••••••"
                icon={<KeyRound className="h-4 w-4" />}
                error={errors.confirmPassword?.message}
                rightElement={
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    className="text-muted-foreground hover:text-foreground transition-colors p-1"
                    disabled={isLoading}
                  >
                    {showConfirmPassword ? (
                      <EyeOff className="h-4 w-4" />
                    ) : (
                      <Eye className="h-4 w-4" />
                    )}
                  </button>
                }
              />
              {confirmPasswordVal && (
                <div className="flex items-center gap-1.5 pl-1 pt-0.5">
                  {passwordVal === confirmPasswordVal ? (
                    <>
                      <CheckCircle2 className="h-3.5 w-3.5 text-success shrink-0" />
                      <span className="text-[11px] font-semibold text-success">Passwords match</span>
                    </>
                  ) : (
                    <>
                      <AlertCircle className="h-3.5 w-3.5 text-danger shrink-0" />
                      <span className="text-[11px] font-semibold text-danger">Passwords do not match</span>
                    </>
                  )}
                </div>
              )}
            </div>

            <Button
              type="submit"
              disabled={isLoading}
              isLoading={isLoading}
              className="w-full mt-2"
            >
              Update Password
            </Button>
          </form>
        </>
      )}
    </div>
  );
}
