"use client";

import React, { useState, useMemo } from "react";
import { FileSpreadsheet, Search, CheckCircle2, Calendar } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { mockDb, type Quotation } from "@/shared/mock/mockData";

export function QuotationListScreen() {
  const [quotes, setQuotes] = useState<Quotation[]>(mockDb.quotations);
  const [search, setSearch] = useState("");

  const filteredQuotes = useMemo(() => {
    return quotes.filter(
      q =>
        q.quoteNo.toLowerCase().includes(search.toLowerCase()) ||
        q.contactName.toLowerCase().includes(search.toLowerCase()) ||
        q.dealName.toLowerCase().includes(search.toLowerCase())
    );
  }, [quotes, search]);

  const handleAction = (id: string, action: string) => {
    if (action === "approve") {
      setQuotes(prev =>
        prev.map(q => (q.id === id ? { ...q, status: "accepted" as const } : q))
      );
      alert("Quotation status updated to Accepted. Opportunity moved to Contract stage.");
    } else {
      alert(`Action '${action}' triggered for Quote ${id}.`);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Quotes & Price Proposals</h1>
          <p className="text-xs text-slate-400">Generate, customize, and issue lodging room block or banquet buffet quotations</p>
        </div>
        <Badge variant="primary" className="text-xs px-2.5 font-bold uppercase bg-blue-100 text-blue-800">
          Template Desk Active
        </Badge>
      </div>

      <Card className="border-slate-100 shadow-sm bg-white">
        <CardContent className="py-3 px-4">
          <div className="relative w-full md:w-72">
            <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search quote reference #, client name..."
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
              <TableHead className="font-semibold text-xs text-slate-500">Quote Reference</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Client Name</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Linked Deal</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Total Price Estimate</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Valid Until</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Status</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500 text-center">Desk Quick Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredQuotes.length > 0 ? (
              filteredQuotes.map(q => (
                <TableRow key={q.id} className="hover:bg-slate-50/70 border-b border-slate-100 transition">
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-800">
                    <span className="flex items-center gap-1.5 text-blue-600">
                      <FileSpreadsheet className="size-3.5 text-slate-400" />
                      {q.quoteNo}
                    </span>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-700">{q.contactName}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600 font-semibold">{q.dealName}</TableCell>
                  <TableCell className="py-3 px-4 text-xs font-black text-slate-800">
                    ${q.amount.toLocaleString()}
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-500 font-semibold">
                    <span className="flex items-center gap-1">
                      <Calendar className="size-3 text-slate-400" />
                      {q.expiryDate}
                    </span>
                  </TableCell>
                  <TableCell className="py-3 px-4">
                    <Badge
                      variant={
                        q.status === "accepted"
                          ? "success"
                          : q.status === "sent"
                          ? "primary"
                          : q.status === "expired"
                          ? "danger"
                          : "default"
                      }
                      size="sm"
                      className="font-bold text-[9px] uppercase"
                    >
                      {q.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-center">
                    {q.status === "sent" ? (
                      <div className="flex justify-center items-center gap-1.5">
                        <Button
                          variant="success"
                          size="sm"
                          onClick={() => handleAction(q.id, "approve")}
                          className="px-2.5 py-1 text-[10px] bg-emerald-600 hover:bg-emerald-700 text-white font-bold"
                        >
                          Mark Approved
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleAction(q.id, "resend")}
                          className="px-2.5 py-1 text-[10px] border-slate-200 text-slate-600 hover:bg-slate-50"
                        >
                          Resend
                        </Button>
                      </div>
                    ) : q.status === "accepted" ? (
                      <span className="text-[10px] text-emerald-600 font-bold flex items-center justify-center gap-1">
                        <CheckCircle2 className="size-3.5" /> Approved
                      </span>
                    ) : (
                      <span className="text-[10px] text-slate-400 italic">No action</span>
                    )}
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow hoverable={false}>
                <TableCell colSpan={7} className="py-8 text-center text-slate-400 text-xs">
                  No price quotes found.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
