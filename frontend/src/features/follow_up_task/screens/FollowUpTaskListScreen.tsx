"use client";

import React, { useState, useMemo } from "react";
import {
  CalendarCheck,
  Plus,
  Search,
  Filter,
  CheckCircle2,
  Clock,
  AlertCircle,
  MoreVertical,
  X,
  Calendar,
  Briefcase,
  User,
  Trash2
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
export type FollowUpTask = {
  id: string;
  title: string;
  description: string;
  dueDate: string;
  priority: "high" | "medium" | "low";
  status: "pending" | "completed" | "overdue";
  linkedEntityName: string;
  assignee: string;
};

export function FollowUpTaskListScreen() {
  const [tasks, setTasks] = useState<FollowUpTask[]>([]);
  const [activeTab, setActiveTab] = useState<"all" | "today" | "upcoming" | "overdue" | "completed">("all");
  const [searchTerm, setSearchTerm] = useState("");
  const [priorityFilter, setPriorityFilter] = useState("all");
  const [assigneeFilter, setAssigneeFilter] = useState("all");
  const [isNewTaskDrawerOpen, setIsNewTaskDrawerOpen] = useState(false);

  // Form State for new task
  const [newTask, setNewTask] = useState({
    title: "",
    description: "",
    dueDate: "",
    priority: "medium" as FollowUpTask["priority"],
    linkedEntityName: "",
    assignee: "John Doe"
  });

  // Toggle Completion
  const handleToggleComplete = (id: string) => {
    setTasks(prev =>
      prev.map(task => {
        if (task.id !== id) return task;
        const nextStatus = task.status === "completed" ? "pending" : "completed";
        return { ...task, status: nextStatus };
      })
    );
  };

  // Quick Reschedule +1 Day
  const handleReschedule = (id: string) => {
    setTasks(prev =>
      prev.map(task => {
        if (task.id !== id) return task;
        const current = new Date(task.dueDate);
        current.setDate(current.getDate() + 1); // add 1 day
        return {
          ...task,
          dueDate: current.toISOString(),
          status: current > new Date() ? "pending" : "overdue"
        };
      })
    );
    alert("Task rescheduled for tomorrow.");
  };

  // Delete Task
  const handleDeleteTask = (id: string) => {
    if (confirm("Are you sure you want to remove this follow-up task?")) {
      setTasks(prev => prev.filter(task => task.id !== id));
    }
  };

  // Form submit handler
  const handleCreateTask = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTask.title || !newTask.dueDate) {
      alert("Please provide a Task Title and Due Date.");
      return;
    }

    const created: FollowUpTask = {
      id: `T-${300 + tasks.length + 1}`,
      title: newTask.title,
      description: newTask.description,
      dueDate: new Date(newTask.dueDate).toISOString(),
      priority: newTask.priority,
      status: "pending",
      linkedEntityName: newTask.linkedEntityName || "None",
      assignee: newTask.assignee
    };

    setTasks([created, ...tasks]);
    setIsNewTaskDrawerOpen(false);
    // Reset
    setNewTask({
      title: "",
      description: "",
      dueDate: "",
      priority: "medium",
      linkedEntityName: "",
      assignee: "John Doe"
    });
  };

  // Filter Tasks by tab and queries
  const filteredTasks = useMemo(() => {
    const todayStr = new Date().toISOString().split("T")[0];

    return tasks.filter(task => {
      // search
      const matchesSearch =
        task.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
        task.description.toLowerCase().includes(searchTerm.toLowerCase());
      
      // priority
      const matchesPriority = priorityFilter === "all" || task.priority === priorityFilter;

      // assignee
      const matchesAssignee = assigneeFilter === "all" || task.assignee === assigneeFilter;

      if (!matchesSearch || !matchesPriority || !matchesAssignee) return false;

      // tab filter
      const taskDateStr = task.dueDate.split("T")[0];
      const isCompleted = task.status === "completed";
      const isOverdue = task.status === "overdue" || (new Date(task.dueDate) < new Date() && !isCompleted);

      switch (activeTab) {
        case "today":
          return taskDateStr === todayStr && !isCompleted;
        case "upcoming":
          return taskDateStr > todayStr && !isCompleted && !isOverdue;
        case "overdue":
          return isOverdue && !isCompleted;
        case "completed":
          return isCompleted;
        case "all":
        default:
          return true;
      }
    });
  }, [tasks, activeTab, searchTerm, priorityFilter, assigneeFilter]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Follow-up Activities</h1>
          <p className="text-xs text-slate-400">Log guest inquiries, wedding catering proposals, and tour deposits tasks</p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="primary"
            size="sm"
            onClick={() => setIsNewTaskDrawerOpen(true)}
            className="gap-1 bg-blue-600 hover:bg-blue-700 font-semibold text-xs text-white"
          >
            <Plus className="size-3.5" />
            <span>Create Task</span>
          </Button>
        </div>
      </div>

      {/* Tabs Menu */}
      <div className="flex border-b border-slate-200 gap-6 text-xs font-bold text-slate-400">
        {[
          { id: "all", label: "All Activities" },
          { id: "today", label: "Due Today" },
          { id: "upcoming", label: "Upcoming" },
          { id: "overdue", label: "Overdue Alert" },
          { id: "completed", label: "Completed Log" }
        ].map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id as any)}
            className={`pb-3 border-b-2 px-1 capitalize transition ${
              activeTab === tab.id
                ? "border-blue-600 text-blue-600"
                : "border-transparent hover:text-slate-700"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Filter toolbar */}
      <Card className="border-slate-100 shadow-sm bg-white">
        <CardContent className="py-3 px-4">
          <div className="flex flex-col md:flex-row items-center gap-3">
            {/* Search */}
            <div className="relative w-full md:w-72">
              <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                placeholder="Search tasks, descriptions..."
                value={searchTerm}
                onChange={e => setSearchTerm(e.target.value)}
                className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition"
              />
            </div>

            {/* Priority filter */}
            <div className="w-full md:w-44 flex items-center gap-1.5">
              <span className="text-[10px] text-slate-400 font-bold shrink-0">Priority:</span>
              <Select value={priorityFilter} onChange={e => setPriorityFilter(e.target.value)} className="w-full py-1.5">
                <option value="all">All Priorities</option>
                <option value="high">High</option>
                <option value="medium">Medium</option>
                <option value="low">Low</option>
              </Select>
            </div>

            {/* Assignee filter */}
            <div className="w-full md:w-44 flex items-center gap-1.5">
              <span className="text-[10px] text-slate-400 font-bold shrink-0">Assignee:</span>
              <Select value={assigneeFilter} onChange={e => setAssigneeFilter(e.target.value)} className="w-full py-1.5">
                <option value="all">All Staff</option>
                <option value="John Doe">John Doe</option>
                <option value="Sarah Connor">Sarah Connor</option>
              </Select>
            </div>

            {/* Counter */}
            <div className="md:ml-auto text-xs text-slate-400">
              Showing <strong className="text-slate-700">{filteredTasks.length}</strong> tasks
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Tasks Grid List */}
      <div className="space-y-3">
        {filteredTasks.length > 0 ? (
          filteredTasks.map(task => {
            const isTaskOverdue =
              task.status === "overdue" ||
              (new Date(task.dueDate) < new Date() && task.status !== "completed");
            
            return (
              <Card
                key={task.id}
                className={`border-slate-100 hover:border-blue-300 shadow-xs transition duration-200 ${
                  task.status === "completed" ? "bg-slate-50/50 opacity-70" : "bg-white"
                }`}
              >
                <CardContent className="p-4 flex items-start gap-4">
                  {/* Complete checkbox */}
                  <button
                    onClick={() => handleToggleComplete(task.id)}
                    className="mt-0.5 shrink-0 focus:outline-none"
                    title="Toggle Complete"
                  >
                    <CheckCircle2
                      className={`size-5 transition ${
                        task.status === "completed"
                          ? "text-emerald-500 fill-emerald-50"
                          : isTaskOverdue
                          ? "text-red-400"
                          : "text-slate-300 hover:text-slate-400"
                      }`}
                    />
                  </button>

                  {/* Task details */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <h3
                        className={`text-xs font-bold text-slate-800 ${
                          task.status === "completed" ? "line-through text-slate-400 font-normal" : ""
                        }`}
                      >
                        {task.title}
                      </h3>
                      <Badge
                        variant={
                          task.priority === "high"
                            ? "danger"
                            : task.priority === "medium"
                            ? "warning"
                            : "default"
                        }
                        size="sm"
                        className="font-bold text-[9px] uppercase px-1.5"
                      >
                        {task.priority}
                      </Badge>
                      {isTaskOverdue && task.status !== "completed" && (
                        <Badge variant="danger" size="sm" className="font-bold text-[9px] bg-red-100 text-red-700">
                          OVERDUE
                        </Badge>
                      )}
                    </div>
                    <p className="text-xs text-slate-500 mt-1">{task.description}</p>

                    {/* Metadata tags */}
                    <div className="flex flex-wrap items-center gap-3 mt-3 text-[10px] text-slate-400 font-medium">
                      <span className="flex items-center gap-1">
                        <Calendar className="size-3.5" />
                        Due: {new Date(task.dueDate).toLocaleString()}
                      </span>
                      <span className="flex items-center gap-1">
                        <Briefcase className="size-3.5" />
                        Deal: <strong className="text-slate-600">{task.linkedEntityName}</strong>
                      </span>
                      <span className="flex items-center gap-1">
                        <User className="size-3.5" />
                        Assigned to: <strong className="text-slate-600">{task.assignee}</strong>
                      </span>
                    </div>
                  </div>

                  {/* Quick controls */}
                  <div className="shrink-0 flex items-center gap-1 ml-4">
                    {task.status !== "completed" && (
                      <button
                        onClick={() => handleReschedule(task.id)}
                        className="px-2 py-1 bg-slate-50 border border-slate-200 text-slate-600 hover:bg-slate-100 rounded text-[10px] font-bold transition"
                        title="Reschedule +1 Day"
                      >
                        +1 Day
                      </button>
                    )}
                    <button
                      onClick={() => handleDeleteTask(task.id)}
                      className="p-1.5 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded transition"
                      title="Delete Task"
                    >
                      <Trash2 className="size-4" />
                    </button>
                  </div>
                </CardContent>
              </Card>
            );
          })
        ) : (
          <div className="py-12 text-center text-slate-400 text-xs border border-dashed border-slate-200 bg-white rounded-xl">
            No tasks listed under this queue tab matching your filters.
          </div>
        )}
      </div>

      {/* Slide-over Drawer for adding Task */}
      {isNewTaskDrawerOpen && (
        <>
          {/* Backdrop */}
          <div
            className="fixed inset-0 bg-slate-900/30 backdrop-blur-xs z-40 transition-opacity"
            onClick={() => setIsNewTaskDrawerOpen(false)}
          />
          {/* Drawer Element */}
          <div className="fixed inset-y-0 right-0 w-full max-w-md bg-white shadow-2xl border-l border-slate-200 z-50 flex flex-col animate-in slide-in-from-right duration-300">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
              <div>
                <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
                  <CalendarCheck className="size-4.5 text-blue-600" />
                  Log Follow-up Activity
                </h3>
                <p className="text-[10px] text-slate-400 mt-0.5">Assign actions to assure hotel deal conversions succeed</p>
              </div>
              <button
                onClick={() => setIsNewTaskDrawerOpen(false)}
                className="p-1 rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
              >
                <X className="size-4.5" />
              </button>
            </div>

            {/* Form */}
            <form onSubmit={handleCreateTask} className="flex-1 overflow-y-auto p-6 space-y-4">
              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Activity Title *</label>
                <Input
                  required
                  placeholder="e.g. Call client to verify headcount, Send invoice block..."
                  value={newTask.title}
                  onChange={e => setNewTask({ ...newTask, title: e.target.value })}
                  className="py-1.5 text-xs"
                />
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Detailed Instructions / Description</label>
                <textarea
                  rows={3}
                  placeholder="Provide checklist items, phone extensions, pricing details..."
                  value={newTask.description}
                  onChange={e => setNewTask({ ...newTask, description: e.target.value })}
                  className="w-full rounded-lg border border-slate-200 bg-slate-50 p-2.5 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition resize-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Priority Level</label>
                  <Select
                    value={newTask.priority}
                    onChange={e => setNewTask({ ...newTask, priority: e.target.value as any })}
                    className="py-1.5"
                  >
                    <option value="low">Low Priority</option>
                    <option value="medium">Medium Priority</option>
                    <option value="high">High Priority</option>
                  </Select>
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Due Date & Time *</label>
                  <Input
                    required
                    type="datetime-local"
                    value={newTask.dueDate}
                    onChange={e => setNewTask({ ...newTask, dueDate: e.target.value })}
                    className="py-1.5 text-xs"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Link to Opportunity/Deal</label>
                  <Input
                    placeholder="e.g. Miller Wedding Booking"
                    value={newTask.linkedEntityName}
                    onChange={e => setNewTask({ ...newTask, linkedEntityName: e.target.value })}
                    className="py-1.5 text-xs"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Staff Owner</label>
                  <Select
                    value={newTask.assignee}
                    onChange={e => setNewTask({ ...newTask, assignee: e.target.value })}
                    className="py-1.5"
                  >
                    <option value="John Doe">John Doe (Manager)</option>
                    <option value="Sarah Connor">Sarah Connor (Sales)</option>
                  </Select>
                </div>
              </div>

              <div className="pt-4 flex gap-3 border-t border-slate-100">
                <Button
                  type="submit"
                  variant="primary"
                  className="w-full bg-blue-600 hover:bg-blue-700 text-xs font-semibold"
                >
                  Create Task Action
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setIsNewTaskDrawerOpen(false)}
                  className="w-full border-slate-200 text-xs text-slate-600"
                >
                  Cancel
                </Button>
              </div>
            </form>
          </div>
        </>
      )}
    </div>
  );
}
