"use client";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import Link from "next/link";
import { Mail, AlertCircle, CheckCircle2, ArrowLeft } from "lucide-react";

import { supabaseAuthService } from "@/services/supabase_auth_service";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";

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
    <div className="rounded-2xl border border-border bg-background/60 p-6.5 shadow-lg backdrop-blur-xl transition-all duration-300">
      <div className="flex flex-col space-y-1">
        <h2 className="text-lg font-bold tracking-tight text-foreground">
          Reset your password
        </h2>
        <p className="text-xs text-muted-foreground">
          We will send a password reset link to your email address.
        </p>
      </div>

      {isSuccess ? (
        <div className="mt-5 space-y-5">
          <div className="flex flex-col items-center gap-3 rounded-xl bg-success/10 p-5 text-center border border-success/15">
            <CheckCircle2 className="h-9 w-9 text-success" />
            <div className="space-y-1">
              <h3 className="text-sm font-bold text-foreground">
                Recovery email sent
              </h3>
              <p className="text-xs text-muted-foreground leading-relaxed">
                Please check your inbox and spam folders for instructions to reset your password.
              </p>
            </div>
          </div>
          <Link href={ROUTE_PATHS.login} className="block w-full">
            <Button variant="outline" className="w-full" leftIcon={<ArrowLeft className="h-4 w-4" />}>
              Back to Sign In
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
                Email Address
              </label>
              <Input
                {...register("email")}
                type="email"
                disabled={isLoading}
                placeholder="name@hotel.com"
                icon={<Mail className="h-4 w-4" />}
                error={errors.email?.message}
              />
            </div>

            <Button
              type="submit"
              disabled={isLoading}
              isLoading={isLoading}
              className="w-full mt-2"
            >
              Send Reset Link
            </Button>

            <Link href={ROUTE_PATHS.login} className="block w-full">
              <Button variant="outline" className="w-full" leftIcon={<ArrowLeft className="h-4 w-4" />}>
                Back to Sign In
              </Button>
            </Link>
          </form>
        </>
      )}
    </div>
  );
}
