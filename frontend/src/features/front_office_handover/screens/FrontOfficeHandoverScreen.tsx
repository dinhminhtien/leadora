"use client";

import React, { useState } from "react";
import { Headphones, CheckCircle2, User, Clock } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { mockDb, type FrontOfficeHandover } from "@/shared/mock/mockData";

export function FrontOfficeHandoverScreen() {
  const [logs, setLogs] = useState<FrontOfficeHandover[]>(mockDb.foHandovers);
  const [shiftNote, setShiftNote] = useState("");

  const handlePostNote = (e: React.FormEvent) => {
    e.preventDefault();
    if (!shiftNote) return;

    const created: FrontOfficeHandover = {
      id: `FO-${Date.now()}`,
      shift: "Afternoon",
      date: new Date().toISOString().split("T")[0],
      agentName: "John Doe",
      notes: shiftNote,
      status: "pending"
    };

    setLogs([created, ...logs]);
    setShiftNote("");
    alert("Shift handover log recorded.");
  };

  const handleAcknowledge = (id: string) => {
    setLogs(prev =>
      prev.map(item => (item.id === id ? { ...item, status: "completed" } : item))
    );
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Front Desk Shift Logs</h1>
          <p className="text-xs text-slate-400">Log guest VIP arrivals, unresolved check-in issues, key deposits, or shuttle requests</p>
        </div>
        <Badge variant="primary" className="text-xs px-2.5 font-bold uppercase bg-blue-100 text-blue-800">
          Front Office Desk
        </Badge>
      </div>

      {/* Quick note logger */}
      <Card className="border-slate-100 shadow-sm bg-white">
        <CardContent className="p-4">
          <form onSubmit={handlePostNote} className="space-y-3">
            <div className="space-y-1">
              <label className="text-xs font-semibold text-slate-600 block">Record Current Shift Note</label>
              <textarea
                required
                rows={2}
                placeholder="Log guest complaints, room issues, pending checkouts, or taxi pickups for the incoming team..."
                value={shiftNote}
                onChange={e => setShiftNote(e.target.value)}
                className="w-full rounded-lg border border-slate-200 bg-slate-50 p-2.5 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition resize-none"
              />
            </div>
            <div className="flex justify-between items-center">
              <span className="text-[10px] text-slate-400">Posting as <strong className="text-slate-600">John Doe (Manager)</strong></span>
              <Button type="submit" variant="primary" size="sm" className="bg-blue-600 hover:bg-blue-700 text-xs font-semibold text-white">
                Post Shift Note
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {/* Logs Table */}
      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50 border-b border-slate-100 text-slate-500">
            <TableRow hoverable={false}>
              <TableHead className="font-semibold text-xs text-slate-500">Shift Name</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Date Logged</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Staff Initiator</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Log Message Details</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Status</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500 text-center">Action Sign-off</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {logs.length > 0 ? (
              logs.map(log => (
                <TableRow key={log.id} className={`hover:bg-slate-50/70 border-b border-slate-100 transition ${log.status === "completed" ? "opacity-60" : ""}`}>
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-800">
                    <span className="flex items-center gap-1.5">
                      <Headphones className="size-3.5 text-slate-400" />
                      {log.shift} Shift
                    </span>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-500 font-semibold">{log.date}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-700 font-bold">{log.agentName}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600 font-medium leading-relaxed max-w-sm">
                    {log.notes}
                  </TableCell>
                  <TableCell className="py-3 px-4">
                    <Badge variant={log.status === "completed" ? "success" : "danger"} size="sm" className="font-bold text-[9px] uppercase">
                      {log.status === "completed" ? "ACKNOWLEDGED" : "PENDING READ"}
                    </Badge>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-center">
                    {log.status !== "completed" ? (
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={() => handleAcknowledge(log.id)}
                        className="px-2.5 py-1 text-[10px] bg-blue-600 hover:bg-blue-700 text-white font-bold"
                      >
                        Sign-off Shift
                      </Button>
                    ) : (
                      <span className="text-[10px] text-emerald-600 font-bold flex items-center justify-center gap-1">
                        <CheckCircle2 className="size-3.5" />
                        Signed off
                      </span>
                    )}
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow hoverable={false}>
                <TableCell colSpan={6} className="py-8 text-center text-slate-400 text-xs">
                  No shift handover logs found.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
