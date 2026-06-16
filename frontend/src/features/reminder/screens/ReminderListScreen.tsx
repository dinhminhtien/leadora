"use client";

import React, { useState } from "react";
import { Clock, Check, AlertTriangle, Sparkles } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
export type Reminder = {
  id: string;
  title: string;
  dueDate: string;
  priority: "high" | "medium" | "low";
  linkedEntityName: string;
};

export function ReminderListScreen() {
  const [reminders, setReminders] = useState<Reminder[]>([]);

  const handleDismiss = (id: string) => {
    setReminders(prev => prev.filter(r => r.id !== id));
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Alert Reminders</h1>
          <p className="text-xs text-slate-400">Timely triggers, shift warnings, and SLA check logs</p>
        </div>
        <Badge variant="danger" className="text-xs px-2.5 font-bold uppercase animate-pulse">
          {reminders.length} Pending Alert{reminders.length !== 1 ? "s" : ""}
        </Badge>
      </div>

      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50 border-b border-slate-100 text-slate-500">
            <TableRow hoverable={false}>
              <TableHead className="font-semibold text-xs text-slate-500">Trigger Status</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Subject / Alert Message</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Trigger DateTime</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Related Deal</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Action Control</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {reminders.length > 0 ? (
              reminders.map(reminder => (
                <TableRow key={reminder.id} className="hover:bg-slate-50/70 border-b border-slate-100 transition">
                  <TableCell className="py-3 px-4">
                    <span className="flex items-center gap-1.5 text-xs">
                      <AlertTriangle className="size-4 text-amber-500 shrink-0" />
                      <Badge variant="warning" size="sm" className="font-bold text-[9px]">
                        {reminder.priority.toUpperCase()}
                      </Badge>
                    </span>
                  </TableCell>
                  <TableCell className="py-3 px-4">
                    <div className="text-xs font-bold text-slate-800">{reminder.title}</div>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-500 font-semibold">
                    <div className="flex items-center gap-1.5">
                      <Clock className="size-3.5" />
                      {reminder.dueDate}
                    </div>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs font-medium text-blue-600">
                    {reminder.linkedEntityName || "System Event"}
                  </TableCell>
                  <TableCell className="py-3 px-4">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleDismiss(reminder.id)}
                      className="px-2.5 py-1 text-[10px] border-slate-200 text-slate-600 hover:bg-slate-50 transition"
                    >
                      <Check className="size-3 mr-1 inline" />
                      Dismiss
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow hoverable={false}>
                <TableCell colSpan={5} className="py-12 text-center text-slate-400 text-xs">
                  <div className="flex flex-col items-center gap-1">
                    <Sparkles className="size-8 text-emerald-500" />
                    <span className="font-bold text-slate-700 mt-1">Excellent! No warnings active</span>
                    <span>All SLA targets and reminders have been successfully cleared.</span>
                  </div>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
