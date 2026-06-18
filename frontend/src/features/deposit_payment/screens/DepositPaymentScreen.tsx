"use client";

import React, { useState, useMemo } from "react";
import { CreditCard, CheckCircle2, Search, Landmark } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
export type DepositPayment = {
  id: string;
  paymentNo: string;
  guestName: string;
  amount: number;
  date: string;
  method: string;
  status: "pending" | "paid";
};

export function DepositPaymentScreen() {
  const [payments, setPayments] = useState<DepositPayment[]>([]);
  const [search, setSearch] = useState("");

  const filteredPayments = useMemo(() => {
    return payments.filter(
      p =>
        p.paymentNo.toLowerCase().includes(search.toLowerCase()) ||
        p.guestName.toLowerCase().includes(search.toLowerCase()) ||
        p.method.toLowerCase().includes(search.toLowerCase())
    );
  }, [payments, search]);

  const handleProcessPayment = (id: string) => {
    setPayments(prev =>
      prev.map(p => (p.id === id ? { ...p, status: "paid" as const } : p))
    );
    alert("Payment verified and matched to invoice registry.");
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Deposit Transactions</h1>
          <p className="text-xs text-slate-400">Match wire transfers, process card holds, and log deposits</p>
        </div>
        <Badge variant="success" className="text-xs px-2.5 font-bold uppercase bg-emerald-100 text-emerald-800">
          Payment Gateway Connected
        </Badge>
      </div>

      <Card className="border-slate-100 shadow-sm bg-white">
        <CardContent className="py-3 px-4">
          <div className="relative w-full md:w-72">
            <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search payment #, guest name, method..."
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
              <TableHead className="font-semibold text-xs text-slate-500">Payment Number</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Guest Name</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Amount Paid</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Payment Date</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Method</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Transaction Status</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500 text-center">Desk Audit Control</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredPayments.length > 0 ? (
              filteredPayments.map(p => (
                <TableRow key={p.id} className="hover:bg-slate-50/70 border-b border-slate-100 transition">
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-800">
                    <span className="flex items-center gap-1.5">
                      <CreditCard className="size-3.5 text-slate-400" />
                      {p.paymentNo}
                    </span>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-700">{p.guestName}</TableCell>
                  <TableCell className="py-3 px-4 text-xs font-black text-slate-800">
                    ${p.amount.toLocaleString('en-US')}
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-500">{p.date}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600 font-semibold uppercase">
                    <span className="flex items-center gap-1">
                      <Landmark className="size-3 text-slate-400" />
                      {p.method}
                    </span>
                  </TableCell>
                  <TableCell className="py-3 px-4">
                    <Badge
                      variant={p.status === "paid" ? "success" : "danger"}
                      size="sm"
                      className="font-bold text-[9px] uppercase"
                    >
                      {p.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-center">
                    {p.status !== "paid" ? (
                      <Button
                        variant="success"
                        size="sm"
                        onClick={() => handleProcessPayment(p.id)}
                        className="px-2.5 py-1 text-[10px] bg-emerald-600 hover:bg-emerald-700 text-white font-bold"
                      >
                        Verify Payment
                      </Button>
                    ) : (
                      <span className="text-[10px] text-emerald-600 font-bold flex items-center justify-center gap-1">
                        <CheckCircle2 className="size-3.5" />
                        Settled
                      </span>
                    )}
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow hoverable={false}>
                <TableCell colSpan={7} className="py-8 text-center text-slate-400 text-xs">
                  No payment transaction records found.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
