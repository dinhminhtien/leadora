import { PublicFeedbackLayout } from "@/app/layouts/PublicFeedbackLayout";

type PublicRouteLayoutProps = {
  children: React.ReactNode;
};

export default function PublicRouteLayout({ children }: PublicRouteLayoutProps) {
  return <PublicFeedbackLayout>{children}</PublicFeedbackLayout>;
}
