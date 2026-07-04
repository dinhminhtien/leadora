"use client";

/*
 * Seeding the editable readiness/note from the freshly fetched detail is a legitimate
 * external-system sync that the react-hooks/set-state-in-effect rule flags as a false positive.
 */
/* eslint-disable react-hooks/set-state-in-effect */

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
  CreditCard,
  Save,
  Inbox,
  Clock3,
  CheckCircle2,
  AlertTriangle,
  ConciergeBell,
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
  useArrivalHandoverSummary,
  useUpdateReadiness,
} from "@/features/front_office_handover/hooks/use_arrival_handovers";
import type {
  ArrivalHandover,
  ReadinessStatus,
} from "@/services/arrival_handover_service";

const PAGE_SIZE = 10;

const READINESS_LABELS: Record<string, string> = {
  PENDING_REVIEW: "Chưa xem xét",
  REVIEWED: "Đã xem xét",
  READY_FOR_ARRIVAL: "Sẵn sàng đón khách",
  NEED_CLARIFICATION: "Cần làm rõ",
};

const STATUS_LABELS: Record<string, string> = {
  SUBMITTED: "Đã gửi",
  ACKNOWLEDGED: "Đã tiếp nhận",
  READY: "Hoàn tất",
};

function readinessClass(value?: string) {
  switch (value) {
    case "READY_FOR_ARRIVAL":
      return "bg-emerald-100 text-emerald-700";
    case "REVIEWED":
      return "bg-blue-100 text-blue-700";
    case "NEED_CLARIFICATION":
      return "bg-rose-100 text-rose-700";
    default:
      return "bg-amber-100 text-amber-700";
  }
}

function Pill({ text, className }: { text: string; className: string }) {
  return (
    <span className={`inline-block rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide ${className}`}>
      {text}
    </span>
  );
}

const SUMMARY_COLORS: Record<string, { ring: string; chip: string; text: string }> = {
  amber: { ring: "ring-amber-300", chip: "bg-amber-100 text-amber-600", text: "text-amber-600" },
  blue: { ring: "ring-blue-300", chip: "bg-blue-100 text-blue-600", text: "text-blue-600" },
  emerald: { ring: "ring-emerald-300", chip: "bg-emerald-100 text-emerald-600", text: "text-emerald-600" },
  rose: { ring: "ring-rose-300", chip: "bg-rose-100 text-rose-600", text: "text-rose-600" },
};

function SummaryCard({
  label,
  value,
  icon,
  color,
  active,
  onClick,
}: {
  label: string;
  value?: number;
  icon: React.ReactNode;
  color: keyof typeof SUMMARY_COLORS;
  active?: boolean;
  onClick?: () => void;
}) {
  const c = SUMMARY_COLORS[color];
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex items-center gap-3 rounded-2xl border bg-white p-3.5 text-left shadow-sm transition hover:shadow-md ${
        active ? `border-transparent ring-2 ${c.ring}` : "border-slate-100"
      }`}
    >
      <span className={`flex size-9 shrink-0 items-center justify-center rounded-xl ${c.chip}`}>
        {icon}
      </span>
      <span className="min-w-0">
        <span className={`block text-lg font-extrabold ${c.text}`}>{value ?? "—"}</span>
        <span className="block truncate text-[11px] font-medium text-slate-500">{label}</span>
      </span>
    </button>
  );
}

function fmtDate(iso?: string) {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("vi-VN");
}

function fmtDateTime(iso?: string) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function FrontOfficeHandoverScreen() {
  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState("");
  const [readinessFilter, setReadinessFilter] = useState("");
  const [arrivalDate, setArrivalDate] = useState("");
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
    arrivalDate: arrivalDate || undefined,
    page,
    size: PAGE_SIZE,
  });

  const summaryQuery = useArrivalHandoverSummary();
  const summary = summaryQuery.data;

  const rows: ArrivalHandover[] = useMemo(
    () => listQuery.data?.data?.content ?? [],
    [listQuery.data],
  );
  const totalPages = listQuery.data?.data?.totalPages ?? 1;
  const totalElements = summary?.total ?? listQuery.data?.data?.totalElements ?? rows.length;

  // Clicking a KPI card filters the list by that readiness.
  const filterBy = (readiness: string) => {
    setReadinessFilter((cur) => (cur === readiness ? "" : readiness));
    setPage(0);
  };

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
            Bàn giao đã gửi tới Lễ tân để chuẩn bị đón khách — xem chi tiết và cập nhật trạng thái sẵn sàng.
          </p>
        </div>
        <div className="flex items-center gap-2.5">
          <span className="text-xs font-semibold text-slate-500">{totalElements} yêu cầu</span>
          <Pill text="Front Office" className="bg-blue-100 text-blue-800" />
        </div>
      </div>

      {/* KPI cards — customer arrival requests by readiness */}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <SummaryCard
          label="Chưa xem xét"
          value={summary?.pendingReview}
          icon={<Inbox className="size-4" />}
          color="amber"
          active={readinessFilter === "PENDING_REVIEW"}
          onClick={() => filterBy("PENDING_REVIEW")}
        />
        <SummaryCard
          label="Đã xem xét"
          value={summary?.reviewed}
          icon={<Clock3 className="size-4" />}
          color="blue"
          active={readinessFilter === "REVIEWED"}
          onClick={() => filterBy("REVIEWED")}
        />
        <SummaryCard
          label="Sẵn sàng đón khách"
          value={summary?.readyForArrival}
          icon={<CheckCircle2 className="size-4" />}
          color="emerald"
          active={readinessFilter === "READY_FOR_ARRIVAL"}
          onClick={() => filterBy("READY_FOR_ARRIVAL")}
        />
        <SummaryCard
          label="Cần làm rõ"
          value={summary?.needClarification}
          icon={<AlertTriangle className="size-4" />}
          color="rose"
          active={readinessFilter === "NEED_CLARIFICATION"}
          onClick={() => filterBy("NEED_CLARIFICATION")}
        />
      </div>

      {/* Toolbar */}
      <Card className="border-slate-100 bg-white shadow-sm">
        <CardContent className="flex flex-col gap-3 p-3 lg:flex-row lg:items-center">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
            <Input
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Tìm theo mã booking hoặc tên khách…"
              className="pl-9"
            />
          </div>
          <div className="lg:w-44">
            <Input
              type="date"
              value={arrivalDate}
              onChange={(e) => {
                setArrivalDate(e.target.value);
                setPage(0);
              }}
              title="Lọc theo ngày đến"
            />
          </div>
          <div className="lg:w-56">
            <Select
              value={readinessFilter}
              onChange={(e) => {
                setReadinessFilter(e.target.value);
                setPage(0);
              }}
            >
              <option value="">Tất cả trạng thái sẵn sàng</option>
              <option value="PENDING_REVIEW">Chưa xem xét</option>
              <option value="REVIEWED">Đã xem xét</option>
              <option value="READY_FOR_ARRIVAL">Sẵn sàng đón khách</option>
              <option value="NEED_CLARIFICATION">Cần làm rõ</option>
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
                <TableHead>Ngày đến</TableHead>
                <TableHead>Phòng / Dịch vụ</TableHead>
                <TableHead>Yêu cầu đặc biệt</TableHead>
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
                  <TableCell colSpan={6} className="py-12">
                    <div className="flex flex-col items-center justify-center gap-2 text-center">
                      <span className="flex size-12 items-center justify-center rounded-full bg-slate-100 text-slate-400">
                        <ConciergeBell className="size-6" />
                      </span>
                      <p className="text-sm font-semibold text-slate-600">
                        {readinessFilter || search || arrivalDate
                          ? "Không có yêu cầu nào khớp bộ lọc"
                          : "Chưa có yêu cầu khách đến nào"}
                      </p>
                      <p className="max-w-xs text-xs text-slate-400">
                        Các bàn giao được Sales/Đặt phòng gửi sang sau khi xác nhận booking sẽ hiển thị ở đây để Lễ tân chuẩn bị đón khách.
                      </p>
                    </div>
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
                  <TableCell className="max-w-[180px] truncate" title={h.roomSummary}>
                    {h.roomSummary || "—"}
                  </TableCell>
                  <TableCell className="max-w-[180px] truncate text-slate-500" title={h.specialRequests}>
                    {h.specialRequests?.trim() ? h.specialRequests : "—"}
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
  const [note, setNote] = useState("");
  const [localError, setLocalError] = useState<string | null>(null);

  useEffect(() => {
    // Seed editable fields from the freshly loaded detail (external-system sync).
    if (!detail) return;
    setReadiness((detail.readinessStatus as ReadinessStatus) ?? "");
    setNote(detail.clarificationNote ?? "");
  }, [detail]);

  const needsClarification = readiness === "NEED_CLARIFICATION";
  const dirty =
    !!readiness &&
    (readiness !== detail?.readinessStatus ||
      (needsClarification && note.trim() !== (detail?.clarificationNote ?? "")));

  const handleSave = async () => {
    setLocalError(null);
    if (!readiness || !dirty) return;
    if (needsClarification && !note.trim()) {
      setLocalError("Vui lòng nhập nội dung cần làm rõ.");
      return;
    }
    await updateReadiness.mutateAsync({
      id,
      readinessStatus: readiness,
      clarificationNote: needsClarification ? note.trim() : undefined,
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      {/* backdrop */}
      <div className="absolute inset-0 bg-slate-900/30" onClick={onClose} />

      <div className="relative z-10 flex h-full w-full max-w-md flex-col overflow-hidden bg-white shadow-2xl animate-in slide-in-from-right duration-200">
        {/* header */}
        <div className="flex items-center justify-between border-b border-slate-100 bg-slate-50 px-4 py-3">
          <div className="min-w-0">
            <p className="text-[10px] font-bold uppercase tracking-widest text-slate-400">
              Chi tiết bàn giao
            </p>
            <h2 className="text-sm font-bold text-slate-800">{detail?.bookingCode || "—"}</h2>
            {detail?.readinessStatus && (
              <div className="mt-1">
                <Pill
                  text={READINESS_LABELS[detail.readinessStatus] ?? detail.readinessStatus}
                  className={readinessClass(detail.readinessStatus)}
                />
              </div>
            )}
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
                <InfoRow label="Trạng thái" value={STATUS_LABELS[detail.status ?? ""] ?? detail.status} />
                <InfoRow
                  label="Thanh toán / cọc"
                  value={detail.paymentReference}
                  icon={<CreditCard className="size-3.5 text-slate-400" />}
                />
                {detail.submittedAt && (
                  <InfoRow label="Gửi tới Lễ tân" value={fmtDateTime(detail.submittedAt)} />
                )}
                {detail.acknowledgedAt && (
                  <InfoRow label="Tiếp nhận lúc" value={fmtDateTime(detail.acknowledgedAt)} />
                )}
              </section>

              {/* Room / service breakdown */}
              <section className="space-y-1">
                <p className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
                  <BedDouble className="size-3.5 text-blue-500" />
                  Phòng / Dịch vụ
                </p>
                <div className="space-y-1 rounded-lg border border-slate-100 bg-white p-2">
                  {detail.rooms && detail.rooms.length > 0 ? (
                    detail.rooms.map((r, i) => (
                      <div key={i} className="flex items-center justify-between text-xs text-slate-600">
                        <span className="font-medium text-slate-700">
                          {r.productName || "Dịch vụ"}
                          {r.roomNumber ? ` · Phòng ${r.roomNumber}` : ""}
                        </span>
                        <span className="text-slate-400">
                          x{r.quantity ?? 1}
                          {r.nights ? ` · ${r.nights} đêm` : ""}
                        </span>
                      </div>
                    ))
                  ) : (
                    <p className="text-xs text-slate-400">—</p>
                  )}
                </div>
              </section>

              {/* Handover notes (read-only for FO) */}
              <NoteBlock icon={<Star className="size-3.5 text-amber-500" />} title="Ghi chú VIP" text={detail.vipNotes} />
              <NoteBlock icon={<BedDouble className="size-3.5 text-blue-500" />} title="Yêu cầu phòng" text={detail.roomPreferences} />
              <NoteBlock icon={<ClipboardList className="size-3.5 text-slate-500" />} title="Yêu cầu đặc biệt" text={detail.specialRequests} />
              <NoteBlock icon={<ClipboardList className="size-3.5 text-slate-500" />} title="Ghi chú vận hành" text={detail.operationalNotes} />

              {/* Readiness update (UC-22.3) */}
              <section className="space-y-2 rounded-xl border border-blue-100 bg-blue-50/40 p-3">
                <p className="text-[11px] font-bold uppercase tracking-wider text-slate-600">
                  Cập nhật trạng thái sẵn sàng
                </p>
                <Select
                  value={readiness}
                  onChange={(e) => {
                    setReadiness(e.target.value as ReadinessStatus);
                    setLocalError(null);
                  }}
                >
                  <option value="PENDING_REVIEW" disabled>
                    Chưa xem xét
                  </option>
                  <option value="REVIEWED">Đã xem xét</option>
                  <option value="READY_FOR_ARRIVAL">Sẵn sàng đón khách</option>
                  <option value="NEED_CLARIFICATION">Cần làm rõ</option>
                </Select>

                {needsClarification && (
                  <textarea
                    rows={3}
                    value={note}
                    onChange={(e) => {
                      setNote(e.target.value);
                      setLocalError(null);
                    }}
                    placeholder="Nội dung cần Sales/Đặt phòng làm rõ…"
                    className="w-full resize-none rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs text-slate-800 placeholder:text-slate-400 focus:border-blue-400 focus:outline-none"
                  />
                )}

                {(localError || updateReadiness.isError) && (
                  <p className="text-[11px] text-rose-500">
                    {localError || "Cập nhật thất bại. Vui lòng thử lại."}
                  </p>
                )}
                {updateReadiness.isSuccess && !dirty && !localError && (
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
                {needsClarification && (
                  <p className="text-[10px] text-slate-400">
                    Khi chọn “Cần làm rõ”, hệ thống sẽ gửi thông báo cho Sales/Đặt phòng phụ trách.
                  </p>
                )}
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
