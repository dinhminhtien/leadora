"use client";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import Link from "next/link";
import { KeyRound, AlertCircle, CheckCircle2, ArrowRight } from "lucide-react";

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
                type="password"
                disabled={isLoading}
                placeholder="••••••••"
                icon={<KeyRound className="h-4 w-4" />}
                error={errors.password?.message}
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
                Confirm New Password
              </label>
              <Input
                {...register("confirmPassword")}
                type="password"
                disabled={isLoading}
                placeholder="••••••••"
                icon={<KeyRound className="h-4 w-4" />}
                error={errors.confirmPassword?.message}
              />
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
