"use client";

import { use } from "react";
import { CustomerProfileDetailScreen } from "@/features/customer_profile/screens/CustomerProfileDetailScreen";
import {
  useCustomerDetail,
  useCustomerTasks,
} from "@/features/customer_profile/hooks/use_customer_profiles";
import type { Task } from "@/services/follow_up_task_service";

interface Props {
  params: Promise<{ id: string }>;
}

function isTaskOverdue(task: Task): boolean {
  if (task.status !== "OPEN") return false;
  if (task.endAt) return new Date(task.endAt) < new Date();
  return false;
}

export default function CustomerProfileDetailPage({ params }: Props) {
  const { id } = use(params);

  const { data: customerData, isLoading: customerLoading } = useCustomerDetail(id);
  const { data: tasksData, isLoading: tasksLoading } = useCustomerTasks(id);

  if (customerLoading || tasksLoading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh] text-slate-400 text-sm">
        Loading customer profile…
      </div>
    );
  }

  const raw = customerData?.data;
  if (!raw) {
    return (
      <div className="flex items-center justify-center min-h-[60vh] text-slate-400 text-sm">
        Customer not found.
      </div>
    );
  }

  const tasks = tasksData?.data?.content ?? [];
  const overdueCount = tasks.filter(isTaskOverdue).length;
  const openCount = tasks.filter((t) => t.status === "OPEN").length;

  const customer = {
    id: raw.id,
    name: raw.name ?? "Unknown",
    email: raw.email ?? "",
    phone: raw.phone ?? "",
    company: raw.company ?? "",
    position: "",
    status: "active" as const,
    assignedStaff: "",
    lastInteraction: "",
    createdDate: "",
  };

  const taskSummaries = tasks.map((t) => ({
    id: t.taskId,
    title: t.title,
    status: t.status,
    priority: t.priority,
    endAt: t.endAt,
    isOverdue: isTaskOverdue(t),
  }));

  const metrics = {
    totalTasks: tasks.length,
    openTasks: openCount,
    overdueTasks: overdueCount,
    totalBookings: 0,
    totalFeedback: 0,
    activeFollowUps: openCount,
    satisfactionScore: 0,
    totalDealValue: 0,
  };

  return (
    <CustomerProfileDetailScreen
      customerId={id}
      customer={customer}
      tasks={taskSummaries}
      activities={[]}
      metrics={metrics}
    />
  );
}
