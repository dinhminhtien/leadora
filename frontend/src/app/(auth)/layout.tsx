import { AuthLayout } from "@/app/layouts/AuthLayout";

type AuthRouteLayoutProps = {
  children: React.ReactNode;
};

export default function AuthRouteLayout({ children }: AuthRouteLayoutProps) {
  return <AuthLayout>{children}</AuthLayout>;
}
