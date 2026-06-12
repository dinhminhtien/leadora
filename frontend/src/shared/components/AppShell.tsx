import { cn } from "@/shared/utils/cn";

type AppShellProps = {
  children: React.ReactNode;
  className?: string;
};

export function AppShell({ children, className }: AppShellProps) {
  return (
    <div className={cn("mx-auto w-full max-w-7xl", className)}>
      {children}
    </div>
  );
}
