import { DashboardLayout } from "@/app/layouts/DashboardLayout";

type DashboardRouteLayoutProps = {
  children: React.ReactNode;
};

export default function DashboardRouteLayout({
  children,
}: DashboardRouteLayoutProps) {
  return <DashboardLayout>{children}</DashboardLayout>;
}
