import React from "react";
import { Card, CardContent, CardDescription, CardTitle } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";

interface KPICardProps {
  title: string;
  value: string | number;
  subtitle?: string;
  trend?: {
    value: number;
    direction: "up" | "down";
    label: string;
  };
  icon?: React.ReactNode;
  className?: string;
}

export const KPICard: React.FC<KPICardProps> = ({
  title,
  value,
  subtitle,
  trend,
  icon,
  className,
}) => {
  return (
    <Card className={`${className || ""}`}>
      <CardContent className="pt-0">
        <div className="flex items-start justify-between gap-4">
          <div className="flex-1">
            <CardDescription className="mb-1">{title}</CardDescription>
            <div className="flex items-baseline gap-2">
              <p className="text-3xl font-bold text-foreground">{value}</p>
              {trend && (
                <Badge
                  variant={trend.direction === "up" ? "success" : "danger"}
                  size="sm"
                >
                  {trend.direction === "up" ? "↑" : "↓"} {trend.value}%
                </Badge>
              )}
            </div>
            {subtitle && (
              <p className="mt-1 text-xs text-muted-foreground">{subtitle}</p>
            )}
          </div>
          {icon && (
            <div className="flex size-12 items-center justify-center rounded-lg bg-primary/10 text-primary shrink-0">
              {icon}
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
};

KPICard.displayName = "KPICard";
