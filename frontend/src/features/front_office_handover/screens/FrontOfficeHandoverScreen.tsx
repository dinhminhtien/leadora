"use client";

import React, { useEffect, useMemo, useState } from "react";
import {
  Headphones,
  Search,
  X,
  Loader2,
  CalendarCheck,
  Phone,
  Star,
  BedDouble,
  ClipboardList,
  Save,
} from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import {
  useArrivalHandovers,
  useArrivalHandoverDetail,
  useUpdateReadiness,
} from "@/features/front_office_handover/hooks/use_arrival_handovers";
import type {
  ArrivalHandover,
  ReadinessStatus,
} from "@/services/arrival_handover_service";

const PAGE_SIZE = 10;

const READINESS_LABELS: Record<string, string> = {
  PENDING: "Chưa chuẩn bị",
  IN_PROGRESS: "Đang chuẩn bị",
  READY: "Sẵn sàng",
};

const STATUS_LABELS: Record<string, string> = {
  SUBMITTED: "Đã gửi",
  ACKNOWLEDGED: "Đã tiếp nhận",
  READY: "Hoàn tất",
};

function readinessClass(value?: string) {
  switch (value) {
    case "READY":
      return "bg-emerald-100 text-emerald-700";
    case "IN_PROGRESS":
      return "bg-blue-100 text-blue-700";
    default:
      return "bg-amber-100 text-amber-700";
  }
}

function statusClass(value?: string) {
  switch (value) {
    case "READY":
      return "bg-emerald-100 text-emerald-700";
    case "ACKNOWLEDGED":
      return "bg-indigo-100 text-indigo-700";
    default:
      return "bg-slate-100 text-slate-600";
  }
}

function Pill({ text, className }: { text: string; className: string }) {
  return (
    <span className={`inline-block rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide ${className}`}>
      {text}
    </span>
  );
}

function fmtDate(iso?: string) {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("vi-VN");
}

export function FrontOfficeHandoverScreen() {
  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState("");
  const [readinessFilter, setReadinessFilter] = useState("");
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  // Debounce the free-text search a touch.
  useEffect(() => {
    const t = setTimeout(() => {
      setSearch(searchInput);
      setPage(0);
    }, 350);
    return () => clearTimeout(t);
  }, [searchInput]);

  const listQuery = useArrivalHandovers({
    search: search || undefined,
    readinessStatus: readinessFilter || undefined,
    page,
    size: PAGE_SIZE,
  });

  const rows: ArrivalHandover[] = useMemo(
    () => listQuery.data?.data?.content ?? [],
    [listQuery.data],
  );
  const totalPages = listQuery.data?.data?.totalPages ?? 1;

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="flex items-center gap-2 text-xl font-bold text-slate-800">
            <Headphones className="size-5 text-blue-600" />
            Bàn giao khách đến (Lễ tân)
          </h1>
          <p className="text-xs text-slate-400">
            Danh sách bàn giao đã gửi tới Lễ tân để chuẩn bị đón khách — cập nhật trạng thái sẵn sàng phòng.
          </p>
        </div>
        <Pill text="Front Office" className="bg-blue-100 text-blue-800" />
      </div>

      {/* Toolbar */}
      <Card className="border-slate-100 bg-white shadow-sm">
        <CardContent className="flex flex-col gap-3 p-3 sm:flex-row sm:items-center">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
            <Input
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Tìm theo mã booking hoặc tên khách…"
              className="pl-9"
            />
          </div>
          <div className="sm:w-56">
            <Select
              value={readinessFilter}
              onChange={(e) => {
                setReadinessFilter(e.target.value);
                setPage(0);
              }}
            >
              <option value="">Tất cả trạng thái sẵn sàng</option>
              <option value="PENDING">Chưa chuẩn bị</option>
              <option value="IN_PROGRESS">Đang chuẩn bị</option>
              <option value="READY">Sẵn sàng</option>
            </Select>
          </div>
        </CardContent>
      </Card>

      {/* List (UC-22.1) */}
      <Card className="border-slate-100 bg-white shadow-sm">
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Mã booking</TableHead>
                <TableHead>Khách</TableHead>
                <TableHead>Nhận phòng</TableHead>
                <TableHead>Trả phòng</TableHead>
                <TableHead>Trạng thái</TableHead>
                <TableHead>Sẵn sàng</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {listQuery.isLoading && (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-xs text-slate-400">
                    <Loader2 className="mx-auto mb-1 size-4 animate-spin" /> Đang tải…
                  </TableCell>
                </TableRow>
              )}
              {!listQuery.isLoading && rows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-xs text-slate-400">
                    Không có bàn giao nào phù hợp.
                  </TableCell>
                </TableRow>
              )}
              {rows.map((h) => (
                <TableRow
                  key={h.handoverId}
                  className="cursor-pointer hover:bg-slate-50"
                  onClick={() => setSelectedId(h.handoverId)}
                >
                  <TableCell className="font-semibold text-slate-700">
                    {h.bookingCode || "—"}
                  </TableCell>
                  <TableCell>{h.customerName || "—"}</TableCell>
                  <TableCell>{fmtDate(h.checkInDate)}</TableCell>
                  <TableCell>{fmtDate(h.checkOutDate)}</TableCell>
                  <TableCell>
                    <Pill text={STATUS_LABELS[h.status ?? ""] ?? h.status ?? "—"} className={statusClass(h.status)} />
                  </TableCell>
                  <TableCell>
                    <Pill
                      text={READINESS_LABELS[h.readinessStatus ?? ""] ?? h.readinessStatus ?? "—"}
                      className={readinessClass(h.readinessStatus)}
                    />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-end gap-2 text-xs">
          <Button
            variant="outline"
            size="sm"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            Trước
          </Button>
          <span className="text-slate-500">
            Trang {page + 1}/{totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            Sau
          </Button>
        </div>
      )}

      {/* Detail slide-over (UC-22.2 + UC-22.3) */}
      {selectedId && (
        <HandoverDetailPanel id={selectedId} onClose={() => setSelectedId(null)} />
      )}
    </div>
  );
}

/* ───────────────────── Detail + readiness update ───────────────────── */

function HandoverDetailPanel({ id, onClose }: { id: string; onClose: () => void }) {
  const detailQuery = useArrivalHandoverDetail(id);
  const updateReadiness = useUpdateReadiness();
  const detail = detailQuery.data?.data;

  const [readiness, setReadiness] = useState<ReadinessStatus | "">("");

  useEffect(() => {
    // Seed the editable readiness from the freshly loaded detail (external-system sync).
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (detail?.readinessStatus) setReadiness(detail.readinessStatus as ReadinessStatus);
  }, [detail?.readinessStatus]);

  const dirty = !!readiness && readiness !== detail?.readinessStatus;

  const handleSave = async () => {
    if (!readiness || !dirty) return;
    await updateReadiness.mutateAsync({ id, readinessStatus: readiness });
  };

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      {/* backdrop */}
      <div className="absolute inset-0 bg-slate-900/30" onClick={onClose} />

      <div className="relative z-10 flex h-full w-full max-w-md flex-col overflow-hidden bg-white shadow-2xl animate-in slide-in-from-right duration-200">
        {/* header */}
        <div className="flex items-center justify-between border-b border-slate-100 bg-slate-50 px-4 py-3">
          <div>
            <p className="text-[10px] font-bold uppercase tracking-widest text-slate-400">
              Chi tiết bàn giao
            </p>
            <h2 className="text-sm font-bold text-slate-800">
              {detail?.bookingCode || "—"}
            </h2>
          </div>
          <button
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-200 hover:text-slate-700"
            title="Đóng"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* body */}
        <div className="custom-scrollbar flex-1 space-y-4 overflow-y-auto p-4">
          {detailQuery.isLoading ? (
            <div className="flex items-center gap-2 text-xs text-slate-400">
              <Loader2 className="size-4 animate-spin" /> Đang tải…
            </div>
          ) : !detail ? (
            <p className="text-xs text-rose-500">Không tải được chi tiết bàn giao.</p>
          ) : (
            <>
              {/* Guest / booking */}
              <section className="space-y-2 rounded-xl border border-slate-100 bg-slate-50/60 p-3">
                <InfoRow label="Khách" value={detail.customerName} />
                <InfoRow
                  label="Liên hệ"
                  value={detail.customerPhone}
                  icon={<Phone className="size-3.5 text-slate-400" />}
                />
                <InfoRow
                  label="Nhận / trả phòng"
                  value={`${fmtDate(detail.checkInDate)} → ${fmtDate(detail.checkOutDate)}`}
                  icon={<CalendarCheck className="size-3.5 text-slate-400" />}
                />
                <InfoRow
                  label="Trạng thái"
                  value={STATUS_LABELS[detail.status ?? ""] ?? detail.status}
                />
              </section>

              {/* Handover notes (read-only for FO) */}
              <NoteBlock
                icon={<Star className="size-3.5 text-amber-500" />}
                title="Ghi chú VIP"
                text={detail.vipNotes}
              />
              <NoteBlock
                icon={<BedDouble className="size-3.5 text-blue-500" />}
                title="Yêu cầu phòng"
                text={detail.roomPreferences}
              />
              <NoteBlock
                icon={<ClipboardList className="size-3.5 text-slate-500" />}
                title="Yêu cầu đặc biệt"
                text={detail.specialRequests}
              />
              <NoteBlock
                icon={<ClipboardList className="size-3.5 text-slate-500" />}
                title="Ghi chú vận hành"
                text={detail.operationalNotes}
              />

              {/* Readiness update (UC-22.3) */}
              <section className="space-y-2 rounded-xl border border-blue-100 bg-blue-50/40 p-3">
                <p className="text-[11px] font-bold uppercase tracking-wider text-slate-600">
                  Cập nhật trạng thái sẵn sàng
                </p>
                <Select
                  value={readiness}
                  onChange={(e) => setReadiness(e.target.value as ReadinessStatus)}
                >
                  <option value="PENDING">Chưa chuẩn bị</option>
                  <option value="IN_PROGRESS">Đang chuẩn bị</option>
                  <option value="READY">Sẵn sàng</option>
                </Select>
                {updateReadiness.isError && (
                  <p className="text-[11px] text-rose-500">
                    Cập nhật thất bại. Vui lòng thử lại.
                  </p>
                )}
                {updateReadiness.isSuccess && !dirty && (
                  <p className="text-[11px] text-emerald-600">Đã cập nhật.</p>
                )}
                <Button
                  size="sm"
                  className="w-full"
                  disabled={!dirty || updateReadiness.isPending}
                  isLoading={updateReadiness.isPending}
                  leftIcon={<Save className="size-3.5" />}
                  onClick={handleSave}
                >
                  Lưu trạng thái
                </Button>
              </section>

              {detail.updatedByName && (
                <p className="text-[10px] text-slate-400">
                  Cập nhật gần nhất bởi {detail.updatedByName}
                </p>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function InfoRow({
  label,
  value,
  icon,
}: {
  label: string;
  value?: string;
  icon?: React.ReactNode;
}) {
  return (
    <div className="flex items-start justify-between gap-3 text-xs">
      <span className="text-slate-400">{label}</span>
      <span className="flex items-center gap-1.5 text-right font-medium text-slate-700">
        {icon}
        {value || "—"}
      </span>
    </div>
  );
}

function NoteBlock({
  icon,
  title,
  text,
}: {
  icon: React.ReactNode;
  title: string;
  text?: string;
}) {
  return (
    <section className="space-y-1">
      <p className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
        {icon}
        {title}
      </p>
      <p className="whitespace-pre-line rounded-lg border border-slate-100 bg-white p-2.5 text-xs text-slate-600">
        {text?.trim() ? text : "—"}
      </p>
    </section>
  );
}
