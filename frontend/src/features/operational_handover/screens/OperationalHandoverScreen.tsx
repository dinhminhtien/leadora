"use client";

import React, { useState, useMemo } from "react";
import { Workflow, CheckCircle2, ClipboardList, Search } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { mockDb, type OperationalHandover } from "@/shared/mock/mockData";

export function OperationalHandoverScreen() {
  const [handovers, setHandovers] = useState<OperationalHandover[]>(mockDb.handovers);
  const [search, setSearch] = useState("");

  const filteredHandovers = useMemo(() => {
    return handovers.filter(
      h =>
        h.handoverNo.toLowerCase().includes(search.toLowerCase()) ||
        h.fromDept.toLowerCase().includes(search.toLowerCase()) ||
        h.toDept.toLowerCase().includes(search.toLowerCase()) ||
        h.notes.toLowerCase().includes(search.toLowerCase())
    );
  }, [handovers, search]);

  const handleCompleteShift = (id: string) => {
    setHandovers(prev =>
      prev.map(h => (h.id === id ? { ...h, status: "completed" } : h))
    );
    alert("Handover checklists fully authorized & checked off.");
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Operational Handover Logs</h1>
          <p className="text-xs text-slate-400">Transfer deal details, room lists, banquet setups, and kitchen instructions to hotel operations</p>
        </div>
        <Badge variant="primary" className="text-xs px-2.5 font-bold uppercase bg-blue-100 text-blue-800">
          Sales → Ops Flow
        </Badge>
      </div>

      <Card className="border-slate-100 shadow-sm bg-white">
        <CardContent className="py-3 px-4">
          <div className="relative w-full md:w-72">
            <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search handover reference, department, notes..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition"
            />
          </div>
        </CardContent>
      </Card>

      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50 border-b border-slate-100 text-slate-500">
            <TableRow hoverable={false}>
              <TableHead className="font-semibold text-xs text-slate-500">Handover Number</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">From Department</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Target Department</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Target Date</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Setup Notes</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Transfer Status</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500 text-center">Action Control</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredHandovers.length > 0 ? (
              filteredHandovers.map(h => (
                <TableRow key={h.id} className="hover:bg-slate-50/70 border-b border-slate-100 transition">
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-800">
                    <span className="flex items-center gap-1.5">
                      <Workflow className="size-3.5 text-blue-500" />
                      {h.handoverNo}
                    </span>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs font-medium text-slate-700">{h.fromDept}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600 font-semibold">{h.toDept}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-500">{h.date}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600 max-w-xs truncate">
                    <div className="flex items-center gap-1">
                      <ClipboardList className="size-3 text-slate-400 shrink-0" />
                      <span>{h.notes}</span>
                    </div>
                  </TableCell>
                  <TableCell className="py-3 px-4">
                    <Badge
                      variant={h.status === "completed" ? "success" : "warning"}
                      size="sm"
                      className="font-bold text-[9px] uppercase"
                    >
                      {h.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-center">
                    {h.status !== "completed" ? (
                      <Button
                        variant="success"
                        size="sm"
                        onClick={() => handleCompleteShift(h.id)}
                        className="px-2.5 py-1 text-[10px] bg-emerald-600 hover:bg-emerald-700 text-white font-bold"
                      >
                        Approve setup
                      </Button>
                    ) : (
                      <span className="text-[10px] text-emerald-600 font-bold flex items-center justify-center gap-1">
                        <CheckCircle2 className="size-3.5" />
                        Acknowledge
                      </span>
                    )}
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow hoverable={false}>
                <TableCell colSpan={7} className="py-8 text-center text-slate-400 text-xs">
                  No handover logs matched your query.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
