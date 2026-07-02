"use client";

import React, { useState } from "react";
import { Loader2, ClipboardList, CheckCircle2, AlertTriangle, XCircle } from "lucide-react";
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
import { useTaskPerformanceReport } from "@/features/reporting/hooks/use_reporting";

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

function PriorityBar({ low, medium, high }: { low: number; medium: number; high: number }) {
  const total = Math.max(1, low + medium + high);
  const seg = (n: number, cls: string, label: string) =>
    n > 0 ? (
      <div
        className={`flex h-full items-center justify-center ${cls}`}
        style={{ width: `${(n / total) * 100}%` }}
        title={`${label}: ${n}`}
      >
        {(n / total) > 0.08 ? n : ""}
      </div>
    ) : null;
  return (
    <div className="space-y-1.5">
      <div className="flex h-5 w-full overflow-hidden rounded-full text-[10px] font-bold text-white">
        {seg(high, "bg-rose-500", "Cao")}
        {seg(medium, "bg-amber-500", "Trung bình")}
        {seg(low, "bg-slate-400", "Thấp")}
      </div>
      <div className="flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-slate-500">
        <span className="flex items-center gap-1"><span className="size-2 rounded-full bg-rose-500" /> Cao: {high}</span>
        <span className="flex items-center gap-1"><span className="size-2 rounded-full bg-amber-500" /> Trung bình: {medium}</span>
        <span className="flex items-center gap-1"><span className="size-2 rounded-full bg-slate-400" /> Thấp: {low}</span>
      </div>
    </div>
  );
}

export function TaskPerformanceTab() {
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  const { data, isLoading, isError } = useTaskPerformanceReport({
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
          <p className="text-[11px] text-slate-400 sm:pb-2">Để trống = toàn bộ thời gian</p>
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
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Kpi label="Tổng công việc" value={String(data.totalTasks)} icon={<ClipboardList className="size-3.5" />} />
            <Kpi
              label="Hoàn thành"
              value={String(data.completed)}
              sub={`Tỉ lệ ${pct(data.completionRate)}`}
              icon={<CheckCircle2 className="size-3.5" />}
              accent="text-emerald-600"
            />
            <Kpi
              label="Quá hạn"
              value={String(data.overdue)}
              sub={`Tỉ lệ ${pct(data.overdueRate)}`}
              icon={<AlertTriangle className="size-3.5" />}
              accent="text-rose-600"
            />
            <Kpi
              label="Đã huỷ"
              value={String(data.cancelled)}
              sub={`Đang mở ${data.open}`}
              icon={<XCircle className="size-3.5" />}
            />
          </div>

          {/* Priority distribution */}
          <Card className="border-slate-100 bg-white shadow-sm">
            <CardContent className="space-y-2 p-4">
              <h3 className="text-sm font-bold text-slate-700">Phân bố theo mức ưu tiên</h3>
              <PriorityBar low={data.priorityLow} medium={data.priorityMedium} high={data.priorityHigh} />
            </CardContent>
          </Card>

          {/* Per-staff breakdown */}
          <Card className="border-slate-100 bg-white shadow-sm">
            <CardContent className="p-0">
              <div className="border-b border-slate-100 px-4 py-3">
                <h3 className="text-sm font-bold text-slate-700">Hiệu suất theo nhân viên</h3>
              </div>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Nhân viên</TableHead>
                    <TableHead className="text-right">Tổng</TableHead>
                    <TableHead className="text-right">Hoàn thành</TableHead>
                    <TableHead className="text-right">Quá hạn</TableHead>
                    <TableHead className="text-right">Tỉ lệ HT</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.staff.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5} className="py-6 text-center text-xs text-slate-400">
                        Không có dữ liệu theo nhân viên trong khoảng thời gian này.
                      </TableCell>
                    </TableRow>
                  )}
                  {data.staff.map((s) => (
                    <TableRow key={s.name}>
                      <TableCell className="font-semibold text-slate-700">{s.name}</TableCell>
                      <TableCell className="text-right">{s.total}</TableCell>
                      <TableCell className="text-right text-emerald-600">{s.completed}</TableCell>
                      <TableCell className="text-right text-rose-600">{s.overdue}</TableCell>
                      <TableCell className="text-right font-semibold">{pct(s.completionRate)}</TableCell>
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
