"use client";

import React, { useState } from "react";
import { Bell, CheckCircle2, Trash2, MailOpen } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { mockDb, type Notification } from "@/shared/mock/mockData";

export function NotificationListScreen() {
  const [alerts, setAlerts] = useState<Notification[]>(mockDb.notifications);

  const handleRead = (id: string) => {
    setAlerts(prev =>
      prev.map(a => (a.id === id ? { ...a, read: true } : a))
    );
  };

  const handleClear = () => {
    setAlerts([]);
    alert("Notification logs cleared.");
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Notification Center</h1>
          <p className="text-xs text-slate-400">Manage real-time shift alerts, VIP lodging check-ins, and SLA escalations</p>
        </div>
        <div className="flex gap-2">
          {alerts.length > 0 && (
            <Button
              variant="outline"
              size="sm"
              onClick={handleClear}
              className="gap-1.5 border-slate-200 text-xs text-slate-600 font-semibold"
            >
              <Trash2 className="size-3.5" />
              Clear Logs
            </Button>
          )}
        </div>
      </div>

      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50 border-b border-slate-100 text-slate-500">
            <TableRow hoverable={false}>
              <TableHead className="font-semibold text-xs text-slate-500">Status</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Alert Message Content</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Type</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Trigger Time</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500 text-center">Action</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {alerts.length > 0 ? (
              alerts.map(alert => (
                <TableRow key={alert.id} className={`hover:bg-slate-50/70 border-b border-slate-100 transition ${alert.read ? "opacity-65" : "bg-blue-50/10"}`}>
                  <TableCell className="py-3 px-4">
                    <span className="flex items-center gap-1.5">
                      <Bell className={`size-4 ${alert.read ? "text-slate-400" : "text-blue-500 animate-swing"}`} />
                      <Badge variant={alert.read ? "default" : "primary"} size="sm" className="font-bold text-[9px] uppercase">
                        {alert.read ? "READ" : "NEW"}
                      </Badge>
                    </span>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600 font-bold max-w-md leading-relaxed">{alert.message}</TableCell>
                  <TableCell className="py-3 px-4">
                    <Badge
                      variant={
                        alert.type === "alert"
                          ? "danger"
                          : alert.type === "success"
                          ? "success"
                          : "default"
                      }
                      size="sm"
                      className="font-bold text-[9px] uppercase"
                    >
                      {alert.type}
                    </Badge>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-500 font-semibold">{alert.time}</TableCell>
                  <TableCell className="py-3 px-4 text-center">
                    {!alert.read ? (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleRead(alert.id)}
                        className="px-2 py-1 text-[10px] border-slate-200 font-bold hover:bg-slate-50 text-slate-600 transition"
                      >
                        <MailOpen className="size-3 mr-1 inline" /> Read
                      </Button>
                    ) : (
                      <span className="text-[10px] text-slate-400 italic">Dismissed</span>
                    )}
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow hoverable={false}>
                <TableCell colSpan={5} className="py-12 text-center text-slate-400 text-xs">
                  <div className="flex flex-col items-center gap-1">
                    <CheckCircle2 className="size-8 text-emerald-500" />
                    <span className="font-bold text-slate-700 mt-1">Inbox cleared! No active warnings</span>
                    <span>All SLA alerts and notifications are up to date.</span>
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
