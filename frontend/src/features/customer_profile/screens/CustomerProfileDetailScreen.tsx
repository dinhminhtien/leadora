"use client";

import React, { useState, useMemo } from "react";
import {
  Phone,
  Mail,
  MapPin,
  Calendar,
  Clock,
  ChevronDown,
  Plus,
  Download,
  MoreVertical,
  Edit2,
  Trash2,
  Share2,
  FileText,
  MessageSquare,
  CheckCircle2,
  AlertCircle,
  TrendingUp,
  Users,
  Building2,
  Briefcase,
  Heart,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/Tabs";

interface Customer {
  id: string;
  name: string;
  email: string;
  phone: string;
  company: string;
  position: string;
  status: "active" | "inactive" | "prospect";
  assignedStaff: string;
  lastInteraction: string;
  createdDate: string;
  avatar?: string;
}

interface SummaryMetrics {
  totalTasks: number;
  openTasks: number;
  overdueTasks: number;
  totalBookings: number;
  totalFeedback: number;
  activeFollowUps: number;
  satisfactionScore: number;
  totalDealValue: number;
}

interface TaskSummary {
  id: string;
  title: string;
  status: "OPEN" | "COMPLETED" | "CANCELLED";
  priority: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  endAt?: string | null;
  isOverdue: boolean;
}

interface ActivityLog {
  id: string;
  type: "task" | "booking" | "feedback" | "call" | "note" | "follow_up";
  title: string;
  description?: string;
  timestamp: string;
  user: string;
}

export interface CustomerProfileDetailScreenProps {
  customerId: string;
  customer: Customer;
  tasks: TaskSummary[];
  activities: ActivityLog[];
  metrics: SummaryMetrics;
}

export function CustomerProfileDetailScreen({
  customerId,
  customer,
  tasks,
  activities,
  metrics,
}: CustomerProfileDetailScreenProps) {
  const [activeTab, setActiveTab] = useState("overview");

  const upcomingTasks = useMemo(
    () => tasks.filter((t) => t.status === "OPEN" && !t.isOverdue),
    [tasks]
  );

  const overdueTasks = useMemo(
    () => tasks.filter((t) => t.status === "OPEN" && t.isOverdue),
    [tasks]
  );

  const completedTasks = useMemo(
    () => tasks.filter((t) => t.status === "COMPLETED"),
    [tasks]
  );

  const recentActivities = useMemo(
    () => activities.slice(0, 10),
    [activities]
  );

  const getStatusColor = (status: string) => {
    switch (status) {
      case "active":
        return "bg-green-100 text-green-700";
      case "inactive":
        return "bg-gray-100 text-gray-700";
      case "prospect":
        return "bg-blue-100 text-blue-700";
      default:
        return "bg-slate-100 text-slate-700";
    }
  };

  const getTaskStatusColor = (status: string, isOverdue?: boolean) => {
    if (isOverdue && status === "OPEN") return "bg-red-100 text-red-700";
    if (status === "OPEN") return "bg-yellow-100 text-yellow-700";
    if (status === "COMPLETED") return "bg-green-100 text-green-700";
    return "bg-slate-100 text-slate-700";
  };

  const getPriorityColor = (priority: string) => {
    switch (priority) {
      case "CRITICAL":
        return "text-red-600";
      case "HIGH":
        return "text-orange-600";
      case "MEDIUM":
        return "text-yellow-600";
      case "LOW":
        return "text-green-600";
      default:
        return "text-slate-600";
    }
  };

  return (
    <div className="space-y-6 p-6 bg-slate-50 min-h-screen">
      {/* Header */}
      <div className="flex justify-between items-start">
        <div className="flex gap-4">
          {customer.avatar ? (
            <img
              src={customer.avatar}
              alt={customer.name}
              className="w-16 h-16 rounded-full object-cover"
            />
          ) : (
            <div className="w-16 h-16 rounded-full bg-blue-100 flex items-center justify-center text-blue-700 font-bold text-lg">
              {customer.name
                .split(" ")
                .map((n) => n[0])
                .join("")}
            </div>
          )}
          <div>
            <h1 className="text-2xl font-bold text-slate-900">{customer.name}</h1>
            <p className="text-sm text-slate-500">{customer.position}</p>
            <Badge className={`mt-2 ${getStatusColor(customer.status)}`}>
              {customer.status.toUpperCase()}
            </Badge>
          </div>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm">
            <Edit2 className="size-4" />
          </Button>
          <Button variant="outline" size="sm">
            <Share2 className="size-4" />
          </Button>
          <Button variant="outline" size="sm">
            <MoreVertical className="size-4" />
          </Button>
        </div>
      </div>

      {/* Contact Information */}
      <Card>
        <CardContent className="pt-6">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="flex items-center gap-2">
              <Mail className="size-4 text-slate-400" />
              <div>
                <p className="text-xs text-slate-500">Email</p>
                <p className="text-sm font-medium text-slate-900">
                  {customer.email}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <Phone className="size-4 text-slate-400" />
              <div>
                <p className="text-xs text-slate-500">Phone</p>
                <p className="text-sm font-medium text-slate-900">
                  {customer.phone}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <Building2 className="size-4 text-slate-400" />
              <div>
                <p className="text-xs text-slate-500">Company</p>
                <p className="text-sm font-medium text-slate-900">
                  {customer.company}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <Users className="size-4 text-slate-400" />
              <div>
                <p className="text-xs text-slate-500">Assigned Staff</p>
                <p className="text-sm font-medium text-slate-900">
                  {customer.assignedStaff}
                </p>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* CRM Summary Metrics */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-xs text-slate-500 mb-1">Total Tasks</p>
                <p className="text-2xl font-bold text-slate-900">
                  {metrics.totalTasks}
                </p>
                <p className="text-xs text-slate-400 mt-1">
                  {metrics.openTasks} open
                </p>
              </div>
              <CheckCircle2 className="size-5 text-blue-500" />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="pt-6">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-xs text-slate-500 mb-1">Overdue Tasks</p>
                <p className="text-2xl font-bold text-slate-900">
                  {metrics.overdueTasks}
                </p>
                <p className="text-xs text-slate-400 mt-1">Needs attention</p>
              </div>
              <AlertCircle className="size-5 text-red-500" />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="pt-6">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-xs text-slate-500 mb-1">Active Follow-ups</p>
                <p className="text-2xl font-bold text-slate-900">
                  {metrics.activeFollowUps}
                </p>
                <p className="text-xs text-slate-400 mt-1">Pending</p>
              </div>
              <TrendingUp className="size-5 text-green-500" />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="pt-6">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-xs text-slate-500 mb-1">Satisfaction</p>
                <p className="text-2xl font-bold text-slate-900">
                  {metrics.satisfactionScore}%
                </p>
                <p className="text-xs text-slate-400 mt-1">Score</p>
              </div>
              <Heart className="size-5 text-red-500" />
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Tabs */}
      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList className="bg-white border-b border-slate-200 rounded-none w-full justify-start">
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="tasks">Tasks ({metrics.totalTasks})</TabsTrigger>
          <TabsTrigger value="follow-ups">Follow-ups</TabsTrigger>
          <TabsTrigger value="bookings">Bookings</TabsTrigger>
          <TabsTrigger value="feedback">Feedback</TabsTrigger>
          <TabsTrigger value="timeline">Activity Timeline</TabsTrigger>
        </TabsList>

        {/* Overview Tab */}
        <TabsContent value="overview" className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center justify-between">
                <span>Upcoming Tasks</span>
                <Button variant="outline" size="sm">
                  <Plus className="size-4 mr-1" />
                  New Task
                </Button>
              </CardTitle>
            </CardHeader>
            <CardContent>
              {upcomingTasks.length > 0 ? (
                <div className="space-y-3">
                  {upcomingTasks.slice(0, 5).map((task) => (
                    <div
                      key={task.id}
                      className="p-3 bg-slate-50 rounded-lg border border-slate-200 hover:border-slate-300 transition"
                    >
                      <div className="flex items-start justify-between">
                        <div className="flex-1">
                          <p className="text-sm font-medium text-slate-900">
                            {task.title}
                          </p>
                          <p className="text-xs text-slate-500 mt-1">
                            Due: {task.endAt ? new Date(task.endAt).toLocaleDateString() : "—"}
                          </p>
                        </div>
                        <div className="flex items-center gap-2">
                          <Badge
                            className={`text-xs ${getTaskStatusColor(
                              task.status,
                              task.isOverdue
                            )}`}
                          >
                            {task.status}
                          </Badge>
                          <Badge
                            className={`text-xs ${getPriorityColor(
                              task.priority
                            )}`}
                          >
                            {task.priority}
                          </Badge>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-slate-500 text-center py-6">
                  No upcoming tasks
                </p>
              )}
            </CardContent>
          </Card>

          {overdueTasks.length > 0 && (
            <Card className="border-red-200 bg-red-50">
              <CardHeader>
                <CardTitle className="text-red-900">
                  ⚠️ Overdue Tasks ({overdueTasks.length})
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="space-y-3">
                  {overdueTasks.map((task) => (
                    <div
                      key={task.id}
                      className="p-3 bg-white rounded-lg border border-red-200 hover:border-red-300 transition"
                    >
                      <div className="flex items-start justify-between">
                        <div className="flex-1">
                          <p className="text-sm font-medium text-slate-900">
                            {task.title}
                          </p>
                          <p className="text-xs text-red-600 mt-1">
                            Due: {task.endAt ? new Date(task.endAt).toLocaleDateString() : "—"}
                          </p>
                        </div>
                        <Badge className="text-xs bg-red-100 text-red-700">
                          OVERDUE
                        </Badge>
                      </div>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}
        </TabsContent>

        {/* Tasks Tab */}
        <TabsContent value="tasks" className="space-y-4">
          <div className="grid grid-cols-3 gap-4 mb-6">
            <Card>
              <CardContent className="pt-4">
                <p className="text-xs text-slate-500">Open Tasks</p>
                <p className="text-2xl font-bold text-blue-600">
                  {metrics.openTasks}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-4">
                <p className="text-xs text-slate-500">Completed</p>
                <p className="text-2xl font-bold text-green-600">
                  {completedTasks.length}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-4">
                <p className="text-xs text-slate-500">Total</p>
                <p className="text-2xl font-bold text-slate-900">
                  {metrics.totalTasks}
                </p>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>All Tasks</CardTitle>
            </CardHeader>
            <CardContent>
              {tasks.length > 0 ? (
                <div className="space-y-3">
                  {tasks.map((task) => (
                    <div
                      key={task.id}
                      className="p-3 bg-slate-50 rounded-lg border border-slate-200 hover:border-slate-300 transition"
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex-1">
                          <p className="text-sm font-medium text-slate-900">
                            {task.title}
                          </p>
                          <p className="text-xs text-slate-500">
                            Due:{" "}
                            {task.endAt ? new Date(task.endAt).toLocaleDateString() : "—"}
                          </p>
                        </div>
                        <div className="flex items-center gap-2">
                          <Badge
                            className={`text-xs ${getTaskStatusColor(
                              task.status,
                              task.isOverdue
                            )}`}
                          >
                            {task.isOverdue ? "OVERDUE" : task.status}
                          </Badge>
                          <Badge className={`text-xs ${getPriorityColor(task.priority)}`}>
                            {task.priority}
                          </Badge>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-slate-500 text-center py-6">
                  No tasks for this customer
                </p>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Follow-ups Tab */}
        <TabsContent value="follow-ups">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center justify-between">
                <span>Follow-up Tasks</span>
                <Button variant="outline" size="sm">
                  <Plus className="size-4 mr-1" />
                  Quick Follow-up
                </Button>
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-slate-500 text-center py-6">
                Manage follow-up tasks and continuation workflows
              </p>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Bookings Tab */}
        <TabsContent value="bookings">
          <Card>
            <CardHeader>
              <CardTitle>Bookings & Reservations</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-slate-500 text-center py-6">
                Total Bookings: {metrics.totalBookings}
              </p>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Feedback Tab */}
        <TabsContent value="feedback">
          <Card>
            <CardHeader>
              <CardTitle>Customer Feedback & Ratings</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-slate-500 text-center py-6">
                Total Feedback: {metrics.totalFeedback}
              </p>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Timeline Tab */}
        <TabsContent value="timeline">
          <Card>
            <CardHeader>
              <CardTitle>Activity Timeline</CardTitle>
            </CardHeader>
            <CardContent>
              {recentActivities.length > 0 ? (
                <div className="space-y-4">
                  {recentActivities.map((activity) => (
                    <div
                      key={activity.id}
                      className="flex gap-4 pb-4 border-b border-slate-200 last:border-0"
                    >
                      <div className="flex-shrink-0">
                        {activity.type === "task" && (
                          <CheckCircle2 className="size-5 text-blue-500" />
                        )}
                        {activity.type === "note" && (
                          <MessageSquare className="size-5 text-green-500" />
                        )}
                        {activity.type === "feedback" && (
                          <Heart className="size-5 text-red-500" />
                        )}
                        {activity.type === "call" && (
                          <Phone className="size-5 text-purple-500" />
                        )}
                        {activity.type === "follow_up" && (
                          <TrendingUp className="size-5 text-orange-500" />
                        )}
                      </div>
                      <div className="flex-1">
                        <p className="text-sm font-medium text-slate-900">
                          {activity.title}
                        </p>
                        {activity.description && (
                          <p className="text-sm text-slate-600 mt-1">
                            {activity.description}
                          </p>
                        )}
                        <p className="text-xs text-slate-500 mt-2">
                          {new Date(activity.timestamp).toLocaleString()} by{" "}
                          {activity.user}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-slate-500 text-center py-6">
                  No activity recorded
                </p>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
