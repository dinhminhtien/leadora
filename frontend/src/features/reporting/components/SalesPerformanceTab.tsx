"use client";

import React, { useState } from "react";
import { Loader2, TrendingUp, Users, BriefcaseBusiness, ReceiptText, Hotel, Banknote } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { useSalesPerformanceReport } from "@/features/reporting/hooks/use_reporting";

const vnd = (n?: number) => `${(n ?? 0).toLocaleString("vi-VN")} ₫`;
const pct = (n?: number) => `${(n ?? 0).toFixed(1)}%`;

function Kpi({
  label,
  value,
  sub,
  icon,
  accent = "text-slate-800",
}: {
  label: string;
  value: string;
  sub?: string;
  icon: React.ReactNode;
  accent?: string;
}) {
  return (
    <Card className="border-slate-100 bg-white shadow-sm">
      <CardContent className="p-4">
        <div className="mb-1 flex items-center gap-2 text-[10px] font-bold uppercase tracking-wider text-slate-400">
          {icon}
          {label}
        </div>
        <p className={`text-lg font-extrabold ${accent}`}>{value}</p>
        {sub && <p className="mt-0.5 text-[11px] text-slate-400">{sub}</p>}
      </CardContent>
    </Card>
  );
}

export function SalesPerformanceTab() {
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  const { data, isLoading, isError } = useSalesPerformanceReport({
    dateFrom: dateFrom || undefined,
    dateTo: dateTo || undefined,
  });

  return (
    <div className="space-y-5">
      {/* Date range */}
      <Card className="border-slate-100 bg-white shadow-sm">
        <CardContent className="flex flex-col gap-3 p-3 sm:flex-row sm:items-end">
          <div className="flex-1">
            <label className="mb-1 block text-[10px] font-bold uppercase tracking-wider text-slate-400">Từ ngày</label>
            <Input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} />
          </div>
          <div className="flex-1">
            <label className="mb-1 block text-[10px] font-bold uppercase tracking-wider text-slate-400">Đến ngày</label>
            <Input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} />
          </div>
          <p className="text-[11px] text-slate-400 sm:pb-2">
            Để trống = toàn bộ thời gian
          </p>
        </CardContent>
      </Card>

      {isLoading && (
        <div className="flex items-center gap-2 p-6 text-sm text-slate-400">
          <Loader2 className="size-4 animate-spin" /> Đang tổng hợp số liệu…
        </div>
      )}
      {isError && <p className="p-4 text-sm text-rose-500">Không tải được báo cáo. Vui lòng thử lại.</p>}

      {data && !isLoading && (
        <>
          {/* KPI grid */}
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Kpi
              label="Lead mới"
              value={String(data.leadsCreated)}
              sub={`Chuyển đổi ${data.leadsConverted} · ${pct(data.leadConversionRate)}`}
              icon={<Users className="size-3.5" />}
            />
            <Kpi
              label="Deal thắng"
              value={`${data.dealsWon}/${data.dealsTotal}`}
              sub={`Tỉ lệ thắng ${pct(data.winRate)} · thua ${data.dealsLost}`}
              icon={<BriefcaseBusiness className="size-3.5" />}
              accent="text-emerald-600"
            />
            <Kpi
              label="Giá trị thắng (WON)"
              value={vnd(data.wonValue)}
              sub={`Pipeline (OPEN) ${vnd(data.pipelineValue)}`}
              icon={<TrendingUp className="size-3.5" />}
              accent="text-emerald-600"
            />
            <Kpi
              label="Doanh thu (đã thu)"
              value={vnd(data.revenue)}
              icon={<Banknote className="size-3.5" />}
              accent="text-blue-600"
            />
            <Kpi
              label="Báo giá"
              value={String(data.quotationsCreated)}
              sub={`Chấp nhận ${data.quotationsAccepted} · ${pct(data.quotationAcceptanceRate)}`}
              icon={<ReceiptText className="size-3.5" />}
            />
            <Kpi
              label="Booking xác nhận"
              value={String(data.bookingsConfirmed)}
              icon={<Hotel className="size-3.5" />}
            />
            <Kpi
              label="Deal đang mở"
              value={String(data.dealsOpen)}
              icon={<BriefcaseBusiness className="size-3.5" />}
            />
            <Kpi
              label="Tỉ lệ chuyển đổi lead"
              value={pct(data.leadConversionRate)}
              icon={<TrendingUp className="size-3.5" />}
            />
          </div>

          {/* Per-rep breakdown */}
          <Card className="border-slate-100 bg-white shadow-sm">
            <CardContent className="p-0">
              <div className="border-b border-slate-100 px-4 py-3">
                <h3 className="text-sm font-bold text-slate-700">Hiệu suất theo nhân viên</h3>
              </div>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Nhân viên</TableHead>
                    <TableHead className="text-right">Lead</TableHead>
                    <TableHead className="text-right">Deal thắng</TableHead>
                    <TableHead className="text-right">Giá trị thắng</TableHead>
                    <TableHead className="text-right">Booking</TableHead>
                    <TableHead className="text-right">Doanh thu</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.reps.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={6} className="py-6 text-center text-xs text-slate-400">
                        Không có dữ liệu theo nhân viên trong khoảng thời gian này.
                      </TableCell>
                    </TableRow>
                  )}
                  {data.reps.map((r) => (
                    <TableRow key={r.name}>
                      <TableCell className="font-semibold text-slate-700">{r.name}</TableCell>
                      <TableCell className="text-right">{r.leads}</TableCell>
                      <TableCell className="text-right text-emerald-600">{r.dealsWon}</TableCell>
                      <TableCell className="text-right">{vnd(r.wonValue)}</TableCell>
                      <TableCell className="text-right">{r.bookings}</TableCell>
                      <TableCell className="text-right font-semibold text-blue-600">{vnd(r.revenue)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
