"use client";

import React, { useState, useEffect, useCallback, useRef } from "react";
import {
  Search, Plus, X, Handshake, Users, TrendingUp, Percent,
  Mail, Phone, Building2, ArrowUpRight, ChevronLeft, ChevronRight,
  Loader2, AlertCircle, SlidersHorizontal, CalendarDays, ArrowUpDown,
  ArrowUp, ArrowDown, ChevronDown, Maximize2, Minimize2, ServerCrash,
} from "lucide-react";
import Link from "next/link";
import type { LeadStatus, CreateLeadPayload, Lead } from "@/services/lead_service";
import { useLeads, useCreateLead } from "@/features/lead/hooks/use_leads";

// ── Constants ─────────────────────────────────────────────────────────────────

const STATUS_CONFIG: Record<LeadStatus, { label: string; dot: string; badge: string }> = {
  NEW:       { label: "New",       dot: "bg-sky-400",     badge: "bg-sky-50 text-sky-700 ring-sky-200" },
  CONTACTED: { label: "Contacted", dot: "bg-amber-400",   badge: "bg-amber-50 text-amber-700 ring-amber-200" },
  QUALIFIED: { label: "Qualified", dot: "bg-emerald-400", badge: "bg-emerald-50 text-emerald-700 ring-emerald-200" },
  CONVERTED: { label: "Converted", dot: "bg-violet-400",  badge: "bg-violet-50 text-violet-700 ring-violet-200" },
  LOST:      { label: "Lost",      dot: "bg-rose-400",    badge: "bg-rose-50 text-rose-700 ring-rose-200" },
};

const SOURCE_OPTIONS = ["Website Inquiry", "Referral", "Social Media", "Cold Call", "Walk-in", "Event"];

const SORT_OPTIONS = [
  { value: "createdAt_desc", label: "Mới nhất",     icon: ArrowDown },
  { value: "createdAt_asc",  label: "Cũ nhất",      icon: ArrowUp },
  { value: "fullName_asc",   label: "Tên A → Z",    icon: ArrowUpDown },
  { value: "fullName_desc",  label: "Tên Z → A",    icon: ArrowUpDown },
];

const EMPTY_FORM: CreateLeadPayload = {
  fullName: "", email: "", phone: "", companyName: "", source: "Website Inquiry", notes: "",
};

// ── Validation ────────────────────────────────────────────────────────────────

type FormErrors = { fullName?: string; email?: string; phone?: string };

function validateForm(f: CreateLeadPayload): FormErrors {
  const err: FormErrors = {};
  if (!f.fullName.trim()) {
    err.fullName = "Họ tên không được để trống";
  } else if (/\d/.test(f.fullName)) {
    err.fullName = "Họ tên không được chứa số";
  }
  if (f.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email)) {
    err.email = "Email không đúng định dạng (phải có @)";
  }
  if (f.phone && !/^\d{10,11}$/.test(f.phone.replace(/\s/g, ""))) {
    err.phone = "Số điện thoại phải là 10–11 chữ số";
  }
  return err;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function Avatar({ name }: { name: string | null }) {
  const initials = (name ?? "?").split(" ").map(p => p[0]).slice(0, 2).join("").toUpperCase();
  const colors = ["bg-blue-100 text-blue-700","bg-violet-100 text-violet-700","bg-emerald-100 text-emerald-700","bg-amber-100 text-amber-700"];
  const color = colors[(name?.charCodeAt(0) ?? 0) % colors.length];
  return (
    <span className={`inline-flex items-center justify-center rounded-full font-bold size-7 text-[10px] ${color}`}>
      {initials}
    </span>
  );
}

function StatusBadge({ status }: { status: LeadStatus }) {
  const cfg = STATUS_CONFIG[status] ?? STATUS_CONFIG.NEW;
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold ring-1 ring-inset ${cfg.badge}`}>
      <span className={`size-1.5 rounded-full ${cfg.dot}`} />
      {cfg.label}
    </span>
  );
}

function FieldError({ msg }: { msg?: string }) {
  if (!msg) return null;
  return <p className="mt-1 text-xs text-rose-500 flex items-center gap-1"><AlertCircle className="size-3" />{msg}</p>;
}

function FilterChip({ label, onRemove }: { label: string; onRemove: () => void }) {
  return (
    <span className="inline-flex items-center gap-1.5 pl-2.5 pr-1.5 py-1 rounded-full text-[11px] font-semibold bg-blue-50 text-blue-700 ring-1 ring-inset ring-blue-200">
      {label}
      <button type="button" onClick={onRemove}
        className="flex items-center justify-center size-3.5 rounded-full hover:bg-blue-200 transition">
        <X className="size-2.5" />
      </button>
    </span>
  );
}

// ── Create Lead Drawer ────────────────────────────────────────────────────────

function CreateLeadDrawer({ onClose }: { onClose: () => void }) {
  const [form, setForm] = useState<CreateLeadPayload>(EMPTY_FORM);
  const [errors, setErrors] = useState<FormErrors>({});
  const [serverError, setServerError] = useState("");
  const createMutation = useCreateLead();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const errs = validateForm(form);
    if (Object.keys(errs).length > 0) { setErrors(errs); return; }
    setErrors({});
    createMutation.mutate(form, {
      onSuccess: onClose,
      onError: (err) => {
        const error = err as { response?: { status?: number; data?: { message?: string } } };
        if (error.response?.status && error.response.status >= 500) {
          setServerError("Sever lỗi vui lòng liên hệ Admin");
        } else {
          setServerError(error.response?.data?.message || "Tạo lead thất bại. Vui lòng thử lại.");
        }
      },
    });
  };

  const field = (key: keyof CreateLeadPayload, value: string) => {
    setForm(f => ({ ...f, [key]: value }));
    if (errors[key as keyof FormErrors]) setErrors(e => ({ ...e, [key]: undefined }));
  };

  return (
    <>
      <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm z-40" onClick={onClose} />
      <aside className="fixed inset-y-0 right-0 z-50 w-full max-w-md bg-white shadow-2xl flex flex-col animate-in slide-in-from-right-4 duration-300">
        <div className="flex items-center justify-between px-6 py-5 border-b bg-gradient-to-r from-blue-600 to-indigo-600">
          <div>
            <h2 className="text-base font-bold text-white">Lead mới</h2>
            <p className="text-blue-200 text-xs mt-0.5">Nhập thông tin khách hàng tiềm năng</p>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-full text-blue-200 hover:bg-white/20 transition">
            <X className="size-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-4">
          {serverError && (
            <div className="flex items-center gap-2 px-3 py-2.5 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-600">
              <ServerCrash className="size-4 shrink-0" />{serverError}
            </div>
          )}

          <div>
            <label className="text-xs font-semibold text-slate-700">Họ và tên <span className="text-rose-500">*</span></label>
            <input placeholder="VD: Nguyễn Văn An" value={form.fullName}
              onChange={e => field("fullName", e.target.value)}
              className={`mt-1 w-full px-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-2 transition
                ${errors.fullName ? "border-rose-300 focus:border-rose-400 focus:ring-rose-100" : "border-slate-200 focus:border-blue-400 focus:ring-blue-100"}`} />
            <FieldError msg={errors.fullName} />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-semibold text-slate-700">Email</label>
              <input type="text" placeholder="example@gmail.com" value={form.email}
                onChange={e => field("email", e.target.value)}
                className={`mt-1 w-full px-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-2 transition
                  ${errors.email ? "border-rose-300 focus:border-rose-400 focus:ring-rose-100" : "border-slate-200 focus:border-blue-400 focus:ring-blue-100"}`} />
              <FieldError msg={errors.email} />
            </div>
            <div>
              <label className="text-xs font-semibold text-slate-700">Số điện thoại</label>
              <input placeholder="0901234567" value={form.phone}
                onChange={e => field("phone", e.target.value)}
                className={`mt-1 w-full px-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-2 transition
                  ${errors.phone ? "border-rose-300 focus:border-rose-400 focus:ring-rose-100" : "border-slate-200 focus:border-blue-400 focus:ring-blue-100"}`} />
              <FieldError msg={errors.phone} />
            </div>
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-700">Công ty / Tổ chức</label>
            <input placeholder="VD: TechCorp Inc." value={form.companyName}
              onChange={e => field("companyName", e.target.value)}
              className="mt-1 w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition" />
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-700">Nguồn tiếp cận</label>
            <select value={form.source} onChange={e => field("source", e.target.value)}
              className="mt-1 w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition cursor-pointer">
              {SOURCE_OPTIONS.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-700">Ghi chú</label>
            <textarea rows={3} placeholder="Mô tả nhu cầu, sự kiện, số phòng…" value={form.notes}
              onChange={e => field("notes", e.target.value)}
              className="mt-1 w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition resize-none" />
          </div>
        </form>

        <div className="flex gap-3 px-6 py-4 border-t bg-slate-50">
          <button type="button" onClick={onClose}
            className="flex-1 px-4 py-2.5 text-sm font-semibold text-slate-600 bg-white border border-slate-200 rounded-xl hover:bg-slate-100 transition">
            Huỷ
          </button>
          <button onClick={handleSubmit} disabled={createMutation.isPending}
            className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-bold text-white bg-blue-600 rounded-xl hover:bg-blue-700 disabled:opacity-60 transition active:scale-95">
            {createMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : <Plus className="size-4" />}
            Tạo Lead
          </button>
        </div>
      </aside>
    </>
  );
}

// ── Lead Table (shared between normal + fullscreen) ───────────────────────────

function LeadTable({
  isLoading, isError, leads, totalPages, totalElements, page, onPageChange, onClearFilters, hasFilters,
}: {
  isLoading: boolean; isError: boolean;
  leads: Lead[]; totalPages: number; totalElements: number;
  page: number; onPageChange: (p: number) => void;
  onClearFilters: () => void; hasFilters: boolean;
}) {
  if (isLoading) return (
    <div className="flex items-center justify-center py-20 gap-2 text-slate-400">
      <Loader2 className="size-5 animate-spin" /> Đang tải…
    </div>
  );

  if (isError) return (
    <div className="flex flex-col items-center justify-center py-20 gap-2 text-rose-500">
      <ServerCrash className="size-8 mb-1" />
      <p className="text-sm font-semibold">Sever lỗi vui lòng liên hệ Admin</p>
    </div>
  );

  if (leads.length === 0) return (
    <div className="flex flex-col items-center justify-center py-20 text-slate-400">
      <Handshake className="size-10 mb-3 opacity-30" />
      <p className="text-sm font-medium">Không tìm thấy kết quả</p>
      {hasFilters && (
        <button onClick={onClearFilters} className="mt-2 text-xs text-blue-500 hover:underline">
          Xoá bộ lọc
        </button>
      )}
    </div>
  );

  const pageStart = Math.max(0, Math.min(page - 2, totalPages - 5));
  const pageNumbers = Array.from({ length: Math.min(5, totalPages) }, (_, i) => pageStart + i);

  return (
    <>
      <div className="overflow-x-auto">
        <table className="w-full text-left">
          <thead className="bg-slate-50 border-b border-slate-100 sticky top-0">
            <tr>
              {[
                { label: "#", w: "w-10" },
                { label: "Tên / Liên hệ", w: "" },
                { label: "Công ty", w: "" },
                { label: "Số điện thoại", w: "" },
                { label: "Nguồn", w: "" },
                { label: "Phụ trách", w: "" },
                { label: "Trạng thái", w: "" },
                { label: "Ngày tạo", w: "" },
                { label: "", w: "w-8" },
              ].map(h => (
                <th key={h.label} className={`px-4 py-3 text-[11px] font-semibold text-slate-500 uppercase tracking-wide whitespace-nowrap ${h.w}`}>
                  {h.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {leads.map((lead, idx) => (
              <tr key={lead.leadId}
                className={`group border-b border-slate-50 hover:bg-blue-50/40 transition-colors ${idx % 2 === 0 ? "bg-white" : "bg-slate-50/30"}`}>
                <td className="px-4 py-3 text-xs text-slate-400 font-mono">
                  {page * 10 + idx + 1}
                </td>
                <td className="px-4 py-3">
                  <Link href={`/leads/${lead.leadId}`}
                    className="font-semibold text-sm text-slate-800 hover:text-blue-600 transition-colors line-clamp-1 group-hover:underline decoration-blue-300 underline-offset-2">
                    {lead.fullName}
                  </Link>
                  {lead.email && <p className="text-[11px] text-slate-400 mt-0.5 line-clamp-1">{lead.email}</p>}
                </td>
                <td className="px-4 py-3">
                  <span className="flex items-center gap-1.5 text-xs text-slate-600">
                    {lead.companyName
                      ? <><Building2 className="size-3.5 text-slate-300 shrink-0" />{lead.companyName}</>
                      : <span className="text-slate-300 italic">—</span>}
                  </span>
                </td>
                <td className="px-4 py-3">
                  {lead.phone
                    ? <a href={`tel:${lead.phone}`} className="flex items-center gap-1 text-xs text-slate-500 hover:text-blue-600">
                        <Phone className="size-3 text-slate-300" />{lead.phone}
                      </a>
                    : <span className="text-slate-300 text-xs italic">—</span>}
                </td>
                <td className="px-4 py-3 text-xs text-slate-500 whitespace-nowrap">
                  {lead.source ?? <span className="text-slate-300 italic">—</span>}
                </td>
                <td className="px-4 py-3">
                  {lead.assignedUserName
                    ? <div className="flex items-center gap-2"><Avatar name={lead.assignedUserName} /><span className="text-xs text-slate-600 truncate max-w-[80px]">{lead.assignedUserName}</span></div>
                    : <span className="text-xs text-slate-300 italic">Chưa phân công</span>}
                </td>
                <td className="px-4 py-3"><StatusBadge status={lead.status} /></td>
                <td className="px-4 py-3 text-xs text-slate-400 whitespace-nowrap">
                  {lead.createdAt ? new Date(lead.createdAt).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" }) : "—"}
                </td>
                <td className="px-4 py-3">
                  <Link href={`/leads/${lead.leadId}`}
                    className="inline-flex items-center gap-1 text-[11px] font-semibold text-blue-600 opacity-0 group-hover:opacity-100 transition-opacity hover:text-blue-800">
                    <ArrowUpRight className="size-3.5" />
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between px-5 py-3 border-t border-slate-100 bg-slate-50/50">
          <p className="text-xs text-slate-500">
            Trang <strong>{page + 1}</strong> / <strong>{totalPages}</strong>
            <span className="text-slate-400 ml-2">· {totalElements} kết quả</span>
          </p>
          <div className="flex items-center gap-1">
            <button onClick={() => onPageChange(Math.max(0, page - 1))} disabled={page === 0}
              className="flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-lg border border-slate-200 text-slate-500 hover:bg-white disabled:opacity-40 disabled:cursor-not-allowed transition">
              <ChevronLeft className="size-3.5" /> Trước
            </button>
            {pageNumbers.map(p => (
              <button key={p} onClick={() => onPageChange(p)}
                className={`size-7 text-xs font-semibold rounded-lg border transition
                  ${p === page ? "bg-blue-600 text-white border-blue-600 shadow-sm" : "border-slate-200 text-slate-500 hover:bg-white"}`}>
                {p + 1}
              </button>
            ))}
            <button onClick={() => onPageChange(Math.min(totalPages - 1, page + 1))} disabled={page >= totalPages - 1}
              className="flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-lg border border-slate-200 text-slate-500 hover:bg-white disabled:opacity-40 disabled:cursor-not-allowed transition">
              Sau <ChevronRight className="size-3.5" />
            </button>
          </div>
        </div>
      )}
    </>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

export function LeadListScreen() {
  const [searchInput, setSearchInput] = useState("");
  const [search,      setSearch]      = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [sourceFilter, setSourceFilter] = useState("");
  const [dateFrom,    setDateFrom]    = useState("");
  const [dateTo,      setDateTo]      = useState("");
  const [sortOption,  setSortOption]  = useState("createdAt_desc");
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [showSortMenu, setShowSortMenu] = useState(false);
  const [page,        setPage]        = useState(0);
  const [drawerOpen,  setDrawer]      = useState(false);
  const [fullScreen,  setFullScreen]  = useState(false);
  const sortRef = useRef<HTMLDivElement>(null);

  // Debounce search
  useEffect(() => {
    const t = setTimeout(() => { setSearch(searchInput); setPage(0); }, 350);
    return () => clearTimeout(t);
  }, [searchInput]);

  // Close sort menu on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (sortRef.current && !sortRef.current.contains(e.target as Node)) setShowSortMenu(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  // Escape key closes fullscreen
  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === "Escape") setFullScreen(false); };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, []);

  const resetPage = useCallback(() => setPage(0), []);
  const [sortBy, sortDir] = sortOption.split("_") as [string, "asc" | "desc"];

  const { data: resp, isLoading, isError } = useLeads({
    search:   search        || undefined,
    status:   statusFilter  || undefined,
    source:   sourceFilter  || undefined,
    sortBy,
    sortDir,
    dateFrom: dateFrom      || undefined,
    dateTo:   dateTo        || undefined,
    page,
    size: 10,
  });

  const pageData      = resp?.data;
  const leads         = pageData?.content ?? [];
  const totalPages    = pageData?.totalPages ?? 1;
  const totalElements = pageData?.totalElements ?? 0;

  const qualified     = leads.filter(l => l.status === "QUALIFIED").length;
  const active        = leads.filter(l => l.status !== "LOST" && l.status !== "CONVERTED").length;

  const activeFilterCount = [statusFilter, sourceFilter, dateFrom, dateTo].filter(Boolean).length;
  const hasFilters    = activeFilterCount > 0 || !!search;

  const clearAll = () => {
    setStatusFilter(""); setSourceFilter(""); setDateFrom(""); setDateTo("");
    setSearchInput(""); setSearch(""); setPage(0);
  };

  const currentSort = SORT_OPTIONS.find(o => o.value === sortOption) ?? SORT_OPTIONS[0];

  // ── Filter bar (reused in both normal + fullscreen) ──────────────────────
  const filterBar = (
    <div className="bg-white border-b border-slate-100">
      <div className="flex flex-wrap items-center gap-2.5 px-4 py-3">
        {/* Search */}
        <div className="relative flex-1 min-w-[180px] max-w-xs">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-3.5 text-slate-400 pointer-events-none" />
          {searchInput && (
            <button type="button" onClick={() => { setSearchInput(""); setSearch(""); setPage(0); }}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 p-0.5 rounded-full hover:bg-slate-200 text-slate-400">
              <X className="size-3" />
            </button>
          )}
          <input type="text" placeholder="Tìm tên, công ty, email…" value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            className="w-full pl-9 pr-8 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 focus:bg-white focus:border-blue-400 focus:outline-none transition" />
        </div>

        {/* Status */}
        <select value={statusFilter} onChange={e => { setStatusFilter(e.target.value); resetPage(); }}
          className="px-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 focus:bg-white focus:border-blue-400 focus:outline-none text-slate-700 cursor-pointer">
          <option value="">Tất cả trạng thái</option>
          {(Object.keys(STATUS_CONFIG) as LeadStatus[]).map(s => (
            <option key={s} value={s}>{STATUS_CONFIG[s].label}</option>
          ))}
        </select>

        {/* Source */}
        <select value={sourceFilter} onChange={e => { setSourceFilter(e.target.value); resetPage(); }}
          className="px-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 focus:bg-white focus:border-blue-400 focus:outline-none text-slate-700 cursor-pointer">
          <option value="">Tất cả nguồn</option>
          {SOURCE_OPTIONS.map(s => <option key={s} value={s}>{s}</option>)}
        </select>

        {/* Sort */}
        <div className="relative" ref={sortRef}>
          <button type="button" onClick={() => setShowSortMenu(v => !v)}
            className="flex items-center gap-2 px-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 hover:bg-white text-slate-600 font-medium transition">
            <currentSort.icon className="size-3.5 text-slate-400" />
            {currentSort.label}
            <ChevronDown className={`size-3 text-slate-400 transition-transform ${showSortMenu ? "rotate-180" : ""}`} />
          </button>
          {showSortMenu && (
            <div className="absolute right-0 top-full mt-1 z-20 bg-white border border-slate-200 rounded-xl shadow-xl py-1 w-40">
              {SORT_OPTIONS.map(opt => (
                <button key={opt.value} type="button"
                  onClick={() => { setSortOption(opt.value); setShowSortMenu(false); resetPage(); }}
                  className={`w-full flex items-center gap-2.5 px-3.5 py-2 text-xs font-medium transition
                    ${sortOption === opt.value ? "bg-blue-50 text-blue-700" : "text-slate-600 hover:bg-slate-50"}`}>
                  <opt.icon className="size-3.5 text-slate-400" />
                  {opt.label}
                  {sortOption === opt.value && <span className="ml-auto size-1.5 rounded-full bg-blue-500" />}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Advanced toggle */}
        <button type="button" onClick={() => setShowAdvanced(v => !v)}
          className={`flex items-center gap-2 px-3 py-2 text-xs border rounded-lg font-semibold transition
            ${showAdvanced || activeFilterCount > 0
              ? "border-blue-300 bg-blue-50 text-blue-700 hover:bg-blue-100"
              : "border-slate-200 bg-slate-50 text-slate-600 hover:bg-white"}`}>
          <SlidersHorizontal className="size-3.5" />
          Bộ lọc
          {activeFilterCount > 0 && (
            <span className="flex items-center justify-center size-4 rounded-full bg-blue-600 text-white text-[9px] font-extrabold">
              {activeFilterCount}
            </span>
          )}
        </button>

        <span className="ml-auto text-xs text-slate-400 hidden sm:block">
          {isLoading ? "Đang tải…" : `${totalElements} kết quả`}
        </span>
      </div>

      {/* Advanced panel */}
      {showAdvanced && (
        <div className="border-t border-slate-100 px-4 py-3 bg-slate-50/60 flex flex-wrap items-end gap-4">
          <div className="flex items-end gap-2">
            <div>
              <label className="flex items-center gap-1 text-[10px] font-bold text-slate-500 uppercase tracking-wide mb-1">
                <CalendarDays className="size-3" /> Từ ngày
              </label>
              <input type="date" value={dateFrom}
                onChange={e => { setDateFrom(e.target.value); resetPage(); }}
                className="px-3 py-2 text-xs border border-slate-200 rounded-lg bg-white focus:border-blue-400 focus:outline-none" />
            </div>
            <span className="text-slate-400 text-xs pb-2.5">→</span>
            <div>
              <label className="flex items-center gap-1 text-[10px] font-bold text-slate-500 uppercase tracking-wide mb-1">
                <CalendarDays className="size-3" /> Đến ngày
              </label>
              <input type="date" value={dateTo}
                onChange={e => { setDateTo(e.target.value); resetPage(); }}
                className="px-3 py-2 text-xs border border-slate-200 rounded-lg bg-white focus:border-blue-400 focus:outline-none" />
            </div>
          </div>
          {hasFilters && (
            <button type="button" onClick={clearAll}
              className="flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-rose-600 bg-rose-50 border border-rose-200 rounded-lg hover:bg-rose-100 transition">
              <X className="size-3" /> Xoá tất cả
            </button>
          )}
        </div>
      )}

      {/* Active chips */}
      {(statusFilter || sourceFilter || dateFrom || dateTo) && (
        <div className="flex flex-wrap items-center gap-2 px-4 pb-3">
          <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wide">Đang lọc:</span>
          {statusFilter && <FilterChip label={`Trạng thái: ${STATUS_CONFIG[statusFilter as LeadStatus]?.label}`} onRemove={() => { setStatusFilter(""); resetPage(); }} />}
          {sourceFilter && <FilterChip label={`Nguồn: ${sourceFilter}`} onRemove={() => { setSourceFilter(""); resetPage(); }} />}
          {dateFrom && <FilterChip label={`Từ: ${new Date(dateFrom).toLocaleDateString("vi-VN")}`} onRemove={() => { setDateFrom(""); resetPage(); }} />}
          {dateTo   && <FilterChip label={`Đến: ${new Date(dateTo).toLocaleDateString("vi-VN")}`}   onRemove={() => { setDateTo("");   resetPage(); }} />}
        </div>
      )}
    </div>
  );

  // ── Full-screen overlay ───────────────────────────────────────────────────
  if (fullScreen) {
    return (
      <div className="fixed inset-0 z-50 bg-white flex flex-col">
        {/* Compact header */}
        <div className="flex items-center justify-between px-5 py-3 border-b border-slate-200 bg-white shrink-0">
          <div className="flex items-center gap-3">
            <span className="font-bold text-slate-800 text-sm">Danh sách Leads</span>
            <span className="text-xs text-slate-400">{totalElements} kết quả</span>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={() => setDrawer(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold text-white bg-blue-600 rounded-lg hover:bg-blue-700 transition active:scale-95">
              <Plus className="size-3.5" /> Lead mới
            </button>
            <button onClick={() => setFullScreen(false)}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-slate-600 border border-slate-200 rounded-lg hover:bg-slate-50 transition"
              title="Thoát toàn màn hình (Esc)">
              <Minimize2 className="size-3.5" /> Thu nhỏ
            </button>
          </div>
        </div>

        {/* Filter bar */}
        <div className="shrink-0">{filterBar}</div>

        {/* Table — scrollable */}
        <div className="flex-1 overflow-auto">
          <LeadTable
            isLoading={isLoading} isError={isError} leads={leads}
            totalPages={totalPages} totalElements={totalElements}
            page={page} onPageChange={setPage}
            onClearFilters={clearAll} hasFilters={hasFilters}
          />
        </div>

        {drawerOpen && <CreateLeadDrawer onClose={() => setDrawer(false)} />}
      </div>
    );
  }

  // ── Normal view ───────────────────────────────────────────────────────────
  return (
    <div className="min-h-full space-y-5">

      {/* Hero */}
      <div className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-blue-600 via-blue-700 to-indigo-800 p-6 shadow-lg">
        <div className="absolute inset-0 opacity-10"
          style={{ backgroundImage: "radial-gradient(circle at 70% 50%, white 1px, transparent 1px)", backgroundSize: "24px 24px" }} />
        <div className="relative flex flex-col sm:flex-row justify-between sm:items-start gap-4">
          <div>
            <p className="text-blue-200 text-xs font-semibold uppercase tracking-widest mb-1">Leadora CRM</p>
            <h1 className="text-2xl font-extrabold text-white tracking-tight">Quản lý Leads</h1>
            <p className="text-blue-200 text-sm mt-1">Theo dõi và chuyển đổi khách hàng tiềm năng</p>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={() => setFullScreen(true)}
              className="flex items-center gap-2 bg-white/10 border border-white/30 text-white font-semibold text-xs px-3 py-2 rounded-xl hover:bg-white/20 transition"
              title="Xem toàn màn hình">
              <Maximize2 className="size-3.5" /> Toàn màn hình
            </button>
            <button onClick={() => setDrawer(true)}
              className="flex items-center gap-2 bg-white text-blue-700 font-bold text-sm px-4 py-2.5 rounded-xl shadow-md hover:bg-blue-50 transition active:scale-95 shrink-0">
              <Plus className="size-4" /> Lead mới
            </button>
          </div>
        </div>

        {/* KPI */}
        <div className="relative mt-6 grid grid-cols-2 sm:grid-cols-4 gap-3">
          {[
            { icon: Handshake,  label: "Tổng Leads",    value: totalElements,                                           color: "text-blue-200" },
            { icon: Users,      label: "Đang theo dõi", value: active,                                                  color: "text-emerald-300" },
            { icon: TrendingUp, label: "Đã Qualified",  value: qualified,                                               color: "text-amber-300" },
            { icon: Percent,    label: "Tỉ lệ Qualify", value: `${((qualified / (leads.length || 1)) * 100).toFixed(0)}%`, color: "text-violet-300" },
          ].map(({ icon: Icon, label, value, color }) => (
            <div key={label} className="bg-white/10 backdrop-blur-sm rounded-xl p-3 border border-white/20">
              <Icon className={`size-4 ${color} mb-2`} />
              <p className="text-white font-extrabold text-xl leading-none">{value}</p>
              <p className="text-blue-200 text-[11px] font-medium mt-0.5">{label}</p>
            </div>
          ))}
        </div>
      </div>

      {/* Filter bar */}
      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        {filterBar}
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        <LeadTable
          isLoading={isLoading} isError={isError} leads={leads}
          totalPages={totalPages} totalElements={totalElements}
          page={page} onPageChange={setPage}
          onClearFilters={clearAll} hasFilters={hasFilters}
        />
      </div>

      {drawerOpen && <CreateLeadDrawer onClose={() => setDrawer(false)} />}
    </div>
  );
}
