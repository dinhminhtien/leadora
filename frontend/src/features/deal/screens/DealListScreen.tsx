"use client";

import React, { useState, useMemo, useEffect, useCallback } from "react";
import {
  Search,
  Plus,
  Filter,
  Briefcase,
  DollarSign,
  User,
  TrendingUp,
  Percent,
  X,
  Calendar,
  CheckCircle2,
  AlertCircle,
  Pencil,
  ChevronRight,
  Trophy,
  XCircle,
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { StatusPill } from "@/components/ui/status-pill";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { dealService, type Deal } from "@/services/deal_service";
import { userService as taskUserService, type UserSummary } from "@/services/follow_up_task_service";
import { customerProfileService, type CustomerSearchItem } from "@/services/customer_profile_service";
import { Portal } from "@/components/ui/Portal";
import { DealWorkflowStepper } from "@/features/deal/components/DealWorkflowStepper";

const STAGES_ORDER: Deal["stage"][] = ["Inquiry", "Qualification", "Proposal", "Negotiation", "Contract", "Confirmed"];



import { UserSelect } from "@/components/ui/UserSelect";
import { PageHeader } from "@/components/ui/page-header";
import { PAGE_META } from "@/app/routes/page_meta";
import { DataTable, type ColumnDef } from "@/components/ui/data-table";
import { DensityMenu } from "@/components/ui/list-toolbar";
import {
  ColumnPicker,
  ExportMenu,
  RefreshButton,
  useTableControls,
} from "@/components/ui/table-controls";
import { RowActions, OwnerCell } from "@/components/ui/row-actions";
import { BlockedHint } from "@/components/ui/guarded-action";

const DEAL_EXPORT_HEADERS = [
  "Title", "Guest", "Email", "Stage", "Probability %", "Value (VND)",
  "Expected close", "Owner", "Status",
];

function dealExportRow(deal: Deal): (string | number | null | undefined)[] {
  return [
    deal.title, deal.contactName, deal.email, deal.stage, deal.probability,
    deal.value, deal.expectedClose, deal.owner, deal.status,
  ];
}
import { useMyProfile } from "@/features/profile/hooks/use_profile";

export function DealListScreen() {
  const { data: profile } = useMyProfile();
  const isManager = useMemo(() => {
    const role = (profile?.roleName || "").toUpperCase();
    return role === "MANAGER" || role === "ADMIN";
  }, [profile]);

  const [deals, setDeals] = useState<Deal[]>([]);
  const [searchTerm, setSearchTerm] = useState("");
  const [stageFilter, setStageFilter] = useState("all");
  const [statusFilter, setStatusFilter] = useState("active");
  const [isNewDealDrawerOpen, setIsNewDealDrawerOpen] = useState(false);
  const [isEditDealDrawerOpen, setIsEditDealDrawerOpen] = useState(false);
  const [editingDeal, setEditingDeal] = useState<Deal | null>(null);

  // Pagination States
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const isAlreadyClosed = useMemo(() => {
    if (!editingDeal) return false;
    const orig = deals.find(d => d.id === editingDeal.id);
    return orig ? orig.status !== "active" : false;
  }, [editingDeal, deals]);

  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const showError = (msg: string) => {
    setErrorMessage(msg);
    setTimeout(() => {
      setErrorMessage(prev => (prev === msg ? null : prev));
    }, 6000);
  };

  const showSuccess = (msg: string) => {
    setSuccessMessage(msg);
    setTimeout(() => {
      setSuccessMessage(prev => (prev === msg ? null : prev));
    }, 4000);
  };

  // Form State for new deal
  const [newDeal, setNewDeal] = useState({
    customerId: "",
    title: "",
    contactName: "",
    email: "",
    phone: "",
    stage: "Inquiry" as Deal["stage"],
    value: "",
    probability: "50",
    owner: "",
    expectedClose: "",
    notes: ""
  });

  const [selectedCustomer, setSelectedCustomer] = useState<CustomerSearchItem | null>(null);
  const [customerSearchQuery, setCustomerSearchQuery] = useState("");
  const [customerResults, setCustomerResults] = useState<CustomerSearchItem[]>([]);
  const [isSearchingCustomers, setIsSearchingCustomers] = useState(false);
  const [showCustomerDropdown, setShowCustomerDropdown] = useState(false);

  useEffect(() => {
    const fetchCustomers = async () => {
      setIsSearchingCustomers(true);
      try {
        const response = await customerProfileService.getList({
          search: customerSearchQuery.trim() || undefined,
          size: 8
        });
        if (response && response.success && response.data) {
          setCustomerResults(response.data);
        }
      } catch (err) {
        console.error("Failed to search customers", err);
      } finally {
        setIsSearchingCustomers(false);
      }
    };

    const delayDebounceFn = setTimeout(() => {
      fetchCustomers();
    }, 300);

    return () => clearTimeout(delayDebounceFn);
  }, [customerSearchQuery]);

  const [users, setUsers] = useState<UserSummary[]>([]);

  useEffect(() => {
    if (profile?.email && !newDeal.owner && !isManager) {
      setNewDeal(prev => ({ ...prev, owner: profile.email }));
    }
  }, [profile, isManager, newDeal.owner]);

  // Hoisted out of the mount effect so the toolbar's Refresh button can call the
  // same loader the screen boots with — one code path, one error message.
  const [isRefreshing, setIsRefreshing] = useState(false);
  const fetchDeals = useCallback(async () => {
    setIsRefreshing(true);
    try {
      const response = await dealService.getList();
      if (response && response.success && response.data) {
        setDeals(response.data as Deal[]);
      }
    } catch (err) {
      console.error("Failed to fetch deals from API", err);
      showError("Could not load deals. Please check your connection and try again.");
    } finally {
      setIsRefreshing(false);
    }
    // `showError` is a stable toast helper.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Load deals and users on component mount
  useEffect(() => {
    const fetchUsers = async () => {
      try {
        const response = await taskUserService.getAll();
        if (response && response.success && response.data) {
          setUsers(response.data);
        }
      } catch (err) {
        console.error("Failed to fetch users from API", err);
      }
    };
    fetchDeals();
    fetchUsers();
  }, [fetchDeals]);

  const getNextStage = (currentStage: Deal["stage"]): Deal["stage"] | null => {
    const idx = STAGES_ORDER.indexOf(currentStage);
    if (idx !== -1 && idx < STAGES_ORDER.length - 1) {
      return STAGES_ORDER[idx + 1];
    }
    return null;
  };

  const checkTransitionConditions = () => ({ met: true });

  const handleStageClick = (targetStage: Deal["stage"]) => {
    if (!editingDeal) return;
    const currentIdx = STAGES_ORDER.indexOf(editingDeal.stage);
    const targetIdx = STAGES_ORDER.indexOf(targetStage);

    if (currentIdx === targetIdx) return;

    // Set stage in local state. Validation will happen on Save when calling backend.
    let updatedStatus = editingDeal.status;
    if (targetStage === "Confirmed") {
      updatedStatus = "won";
    } else if (editingDeal.stage === "Confirmed") {
      updatedStatus = "active";
    }

    const updated = {
      ...editingDeal,
      stage: targetStage,
      status: updatedStatus
    };

    setEditingDeal(updated);
  };

  const handleAdvanceStageQuick = async (deal: Deal) => {
    const nextStg = getNextStage(deal.stage);
    if (!nextStg) return;

    let updatedStatus = deal.status;
    if (nextStg === "Confirmed") {
      updatedStatus = "won";
    }

    const payload = {
      title: deal.title,
      contactName: deal.contactName,
      email: deal.email || "",
      phone: deal.phone || "",
      value: deal.value,
      stage: nextStg,
      status: updatedStatus,
      expectedClose: deal.expectedClose,
      owner: deal.ownerEmail || deal.owner,
      notes: deal.notes || ""
    };

    try {
      const response = await dealService.update(deal.id, payload);
      if (response && response.success && response.data) {
        setDeals(prev =>
          prev.map(d => (d.id === deal.id ? (response.data as Deal) : d))
        );
        showSuccess(`Advanced deal to ${nextStg} successfully!`);
      } else {
        showError(response?.message || "Failed to advance stage");
      }
    } catch (err: any) {
      console.error("Error advancing deal stage", err);
      const errMsg = err.response?.data?.message || err.message || "An error occurred while updating stage.";
      showError(errMsg);
    }
  };

  // Filter Logic
  const filteredDeals = useMemo(() => {
    return deals.filter(deal => {
      const titleLower = deal.title ? deal.title.toLowerCase() : "";
      const contactLower = deal.contactName ? deal.contactName.toLowerCase() : "";
      const matchesSearch =
        titleLower.includes(searchTerm.toLowerCase()) ||
        contactLower.includes(searchTerm.toLowerCase());

      const matchesStage = stageFilter === "all" || deal.stage === stageFilter;
      const matchesStatus = statusFilter === "all" || deal.status === statusFilter;

      return matchesSearch && matchesStage && matchesStatus;
    });
  }, [deals, searchTerm, stageFilter, statusFilter]);

  // Reset page when filters change
  useEffect(() => {
    setCurrentPage(1);
  }, [searchTerm, stageFilter, statusFilter]);

  const totalPages = Math.max(1, Math.ceil(filteredDeals.length / pageSize));

  const paginatedDeals = useMemo(() => {
    const startIndex = (currentPage - 1) * pageSize;
    return filteredDeals.slice(startIndex, startIndex + pageSize);
  }, [filteredDeals, currentPage, pageSize]);

  /**
   * Column set — Blueprint §10.6.
   *
   * Row actions keep their existing behaviour exactly; the only change is that a
   * closed deal now says *why* its actions are gone (BR-44: Closed Won / Closed
   * Lost records are locked) instead of collapsing to the word "Closed".
   */
  const dealColumns: ColumnDef<Deal>[] = useMemo(() => [
    {
      id: "title",
      header: "Deal Title",
      sticky: "left",
      cell: (deal) => (
        <button
          onClick={() => handleOpenEditDrawer(deal)}
          className="text-left text-xs font-bold text-primary transition hover:underline"
        >
          {deal.title}
        </button>
      ),
    },
    {
      id: "guest",
      header: "Primary Guest",
      minWidth: "md",
      cell: (deal) => (
        <>
          <div className="text-xs font-semibold text-foreground">{deal.contactName}</div>
          <div className="mt-0.5 text-[10px] text-muted-foreground">{deal.email}</div>
        </>
      ),
    },
    {
      id: "stage",
      header: "Sales Stage",
      // Canonical pipeline-stage binding (Blueprint §2.7).
      cell: (deal) => <StatusPill size="sm" domain="dealStage" value={deal.stage} />,
    },
    {
      id: "probability",
      header: "Probability",
      numeric: true,
      minWidth: "lg",
      cell: (deal) => `${deal.probability}%`,
    },
    {
      id: "value",
      header: "Deal Value",
      numeric: true,
      className: "font-bold",
      cell: (deal) => `${deal.value.toLocaleString("vi-VN")} ₫`,
    },
    {
      id: "close",
      header: "Close Date",
      minWidth: "lg",
      cell: (deal) => (
        <div className="flex items-center gap-1 text-xs text-muted-foreground">
          <Calendar className="size-3" />
          {deal.expectedClose}
        </div>
      ),
    },
    {
      id: "owner",
      header: "Owner",
      minWidth: "xl",
      cell: (deal) => <OwnerCell name={deal.owner} />,
    },
    {
      id: "status",
      header: "Status",
      cell: (deal) => (
        <Badge
          variant={deal.status === "won" ? "success" : deal.status === "active" ? "primary" : "danger"}
          size="sm"
          className="font-bold text-[10px] uppercase"
        >
          {deal.status}
        </Badge>
      ),
    },
    {
      id: "actions",
      header: "",
      width: "w-12",
      sticky: "right",
      // Four inline buttons per row spent most of the row's width on controls and
      // pushed the actual deal data into truncation. The blueprint's overflow menu
      // keeps every action reachable while the row stays scannable.
      cell: (deal) => {
        // BR-44 — a won/lost deal is locked. The actions stay listed and say why
        // they are unavailable rather than silently disappearing.
        const lockedReason =
          deal.status !== "active"
            ? `This deal is ${deal.status === "won" ? "Closed Won" : "Closed Lost"}. Closed deals are locked (BR-44) and can only be changed through an authorised correction.`
            : null;

        // BR-12 — the pipeline advances one stage at a time and stops at Confirmed.
        const advanceReason =
          lockedReason ??
          (deal.stage === "Confirmed"
            ? "This deal is already at the final pipeline stage."
            : null);

        return (
          <div className="flex justify-end">
            <RowActions
              label="Deal actions"
              actions={[
                {
                  key: "edit",
                  label: "Edit deal",
                  icon: Pencil,
                  onSelect: () => handleOpenEditDrawer(deal),
                },
                {
                  key: "advance",
                  label: advanceReason ? "Next stage" : `Advance to ${getNextStage(deal.stage)}`,
                  icon: ChevronRight,
                  reason: advanceReason,
                  onSelect: () => handleAdvanceStageQuick(deal),
                },
                {
                  key: "won",
                  label: "Mark as Won",
                  icon: Trophy,
                  tone: "success",
                  reason: lockedReason,
                  separatorBefore: true,
                  onSelect: () => handleUpdateStatus(deal.id, "won"),
                },
                {
                  key: "lost",
                  label: "Mark as Lost",
                  icon: XCircle,
                  tone: "danger",
                  reason: lockedReason,
                  onSelect: () => handleUpdateStatus(deal.id, "lost"),
                },
              ]}
            />
          </div>
        );
      },
    },
    // Handlers are stable for the life of the screen; deals data drives re-render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  const controls = useTableControls<Deal>("deals", dealColumns);

  // Statistics
  const stats = useMemo(() => {
    const activeDeals = deals.filter(d => d.status === "active");
    const activeVal = activeDeals.reduce((sum, d) => sum + (d.value || 0), 0);
    const wonDeals = deals.filter(d => d.status === "won");
    const wonVal = wonDeals.reduce((sum, d) => sum + (d.value || 0), 0);
    const totalWins = wonDeals.length;
    const totalClosed = deals.filter(d => d.status !== "active").length;
    const winRate = totalClosed > 0 ? (totalWins / totalClosed) * 100 : 0;

    return {
      activeCount: activeDeals.length,
      activeValue: activeVal,
      wonValue: wonVal,
      winRate: winRate
    };
  }, [deals]);

  // Handle Close Status change (Won/Lost)
  const handleUpdateStatus = async (dealId: string, newStatus: Deal["status"]) => {
    const dealToUpdate = deals.find(d => d.id === dealId);
    if (!dealToUpdate) return;

    const updatedStage = newStatus === "won" ? "Confirmed" : dealToUpdate.stage;
    const payload = {
      title: dealToUpdate.title,
      contactName: dealToUpdate.contactName,
      email: dealToUpdate.email || "",
      phone: dealToUpdate.phone || "",
      value: dealToUpdate.value,
      stage: updatedStage,
      status: newStatus,
      expectedClose: dealToUpdate.expectedClose,
      owner: dealToUpdate.ownerEmail || dealToUpdate.owner,
      notes: dealToUpdate.notes || ""
    };

    try {
      const response = await dealService.update(dealId, payload);
      if (response && response.success && response.data) {
        setDeals(prev =>
          prev.map(deal =>
            deal.id === dealId ? (response.data as Deal) : deal
          )
        );
        showSuccess(`Deal marked as ${newStatus.toUpperCase()}!`);
      } else {
        showError(response?.message || "Failed to update status");
      }
    } catch (err: any) {
      console.error("Error updating status", err);
      const errMsg = err.response?.data?.message || err.message || "An error occurred while updating status.";
      showError(errMsg);
    }
  };

  // Form Submit
  const handleCreateDeal = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newDeal.title) {
      showError("Please enter a Deal title.");
      return;
    }
    if (!newDeal.customerId) {
      showError("Please select an existing customer. Deal funnel requires a customer mapping.");
      return;
    }

    const payload = {
      customerId: newDeal.customerId,
      title: newDeal.title,
      contactName: newDeal.contactName,
      email: newDeal.email || "",
      phone: newDeal.phone || "",
      stage: newDeal.stage,
      value: Number(newDeal.value) || 0,
      expectedClose: newDeal.expectedClose || new Date().toISOString().split("T")[0],
      status: "active",
      owner: newDeal.owner,
      notes: newDeal.notes || ""
    };

    try {
      const response = await dealService.create(payload);
      if (response && response.success && response.data) {
        setDeals(prev => [response.data as Deal, ...prev]);
        setIsNewDealDrawerOpen(false);
        // Reset Form
        setNewDeal({
          customerId: "",
          title: "",
          contactName: "",
          email: "",
          phone: "",
          stage: "Inquiry",
          value: "",
          probability: "50",
          owner: isManager ? "" : (profile?.email || ""),
          expectedClose: "",
          notes: ""
        });
        setSelectedCustomer(null);
        setCustomerSearchQuery("");
        showSuccess("Deal created successfully!");
      } else {
        showError(response?.message || "Failed to create deal");
      }
    } catch (err: any) {
      console.error("Error creating deal", err);
      const errMsg = err.response?.data?.message || err.message || "An error occurred while creating the deal.";
      showError(errMsg);
    }
  };

  const handleOpenEditDrawer = async (deal: Deal) => {
    try {
      const response = await dealService.getById(deal.id);
      if (response && response.success && response.data) {
        setEditingDeal(response.data as Deal);
      } else {
        setEditingDeal(deal);
      }
    } catch (err) {
      console.error("Failed to fetch deal details", err);
      setEditingDeal(deal);
    }
    setIsEditDealDrawerOpen(true);
  };

  const handleUpdateDeal = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingDeal || !editingDeal.title || !editingDeal.contactName) {
      showError("Please enter both Deal title and Primary Contact name.");
      return;
    }

    const payload = {
      title: editingDeal.title,
      contactName: editingDeal.contactName,
      email: editingDeal.email || "",
      phone: editingDeal.phone || "",
      value: Number(editingDeal.value) || 0,
      stage: editingDeal.stage,
      expectedClose: editingDeal.expectedClose,
      status: editingDeal.status,
      owner: editingDeal.ownerEmail || editingDeal.owner,
      notes: editingDeal.notes || ""
    };

    try {
      const response = await dealService.update(editingDeal.id, payload);
      if (response && response.success && response.data) {
        setDeals(prev =>
          prev.map(deal =>
            deal.id === editingDeal.id ? (response.data as Deal) : deal
          )
        );
        setIsEditDealDrawerOpen(false);
        setEditingDeal(null);
        showSuccess("Deal updated successfully!");
      } else {
        showError(response?.message || "Failed to update deal");
      }
    } catch (err: any) {
      console.error("Error updating deal", err);
      const errMsg = err.response?.data?.message || err.message || "An error occurred while updating the deal.";
      showError(errMsg);
    }
  };


  return (
    <div className="space-y-6">
      {/* Toast Banners */}
      {errorMessage && (
        <div className="fixed top-4 right-4 z-100 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg shadow-lg flex items-center gap-2 animate-in fade-in slide-in-from-top duration-300">
          <AlertCircle className="size-4 shrink-0" />
          <span className="text-xs font-semibold">{errorMessage}</span>
          <button type="button" onClick={() => setErrorMessage(null)} className="ml-2 hover:text-red-900">
            <X className="size-3.5" />
          </button>
        </div>
      )}

      {successMessage && (
        <div className="fixed top-4 right-4 z-100 bg-emerald-50 border border-emerald-200 text-emerald-700 px-4 py-3 rounded-lg shadow-lg flex items-center gap-2 animate-in fade-in slide-in-from-top duration-300">
          <CheckCircle2 className="size-4 shrink-0" />
          <span className="text-xs font-semibold">{successMessage}</span>
          <button type="button" onClick={() => setSuccessMessage(null)} className="ml-2 hover:text-emerald-900">
            <X className="size-3.5" />
          </button>
        </div>
      )}
      <PageHeader
        {...PAGE_META.deals}
        actions={
          <Button
            variant="primary"
            onClick={() => setIsNewDealDrawerOpen(true)}
            leftIcon={<Plus className="size-4" />}
          >
            New Deal
          </Button>
        }
      />

      {/* Stats Summary cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 bg-white p-4 rounded-xl border border-slate-100 shadow-sm">
        <div className="border-r border-slate-100 last:border-0 pr-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase">Active Deals</p>
          <p className="text-lg font-bold text-slate-800 mt-1">{stats.activeCount} Deals</p>
        </div>
        <div className="border-r border-slate-100 last:border-0 px-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase">Pipeline Value</p>
          <p className="text-lg font-bold text-slate-800 mt-1">{stats.activeValue.toLocaleString('vi-VN')} ₫</p>
        </div>
        <div className="border-r border-slate-100 last:border-0 px-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase">Won Revenue</p>
          <p className="text-lg font-bold text-slate-800 mt-1">{stats.wonValue.toLocaleString('vi-VN')} ₫</p>
        </div>
        <div className="px-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase">Win Ratio (Closed)</p>
          <p className="text-lg font-bold text-slate-800 mt-1">{stats.winRate.toFixed(1)}%</p>
        </div>
      </div>

      {/* Filters bar */}
      <Card className="border-slate-100 shadow-sm">
        <CardContent className="py-3 px-4">
          <div className="flex flex-col md:flex-row items-center gap-3">
            {/* Search */}
            <div className="relative w-full md:w-72">
              <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                placeholder="Search deal name, guest..."
                value={searchTerm}
                onChange={e => setSearchTerm(e.target.value)}
                className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white transition"
              />
            </div>

            {/* Stage Selector */}
            <div className="w-full md:w-40 flex items-center gap-1.5">
              <span className="text-[10px] text-slate-400 font-bold shrink-0">Stage:</span>
              <Select value={stageFilter} onChange={e => setStageFilter(e.target.value)} className="w-full py-1.5">
                <option value="all">All</option>
                <option value="Inquiry">Inquiry</option>
                <option value="Qualification">Qualification</option>
                <option value="Proposal">Proposal</option>
                <option value="Negotiation">Negotiation</option>
                <option value="Contract">Contract</option>
                <option value="Confirmed">Confirmed</option>
              </Select>
            </div>

            {/* Status Selector */}
            <div className="w-full md:w-40 flex items-center gap-1.5">
              <span className="text-[10px] text-slate-400 font-bold shrink-0">Status:</span>
              <Select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} className="w-full py-1.5">
                <option value="all">All</option>
                <option value="active">Active</option>
                <option value="won">Won</option>
                <option value="lost">Lost</option>
              </Select>
            </div>

            {/* §2.6 control cluster + count indicator */}
            <div className="flex items-center gap-2 md:ml-auto">
              <RefreshButton onRefresh={fetchDeals} isRefreshing={isRefreshing} />
              <ColumnPicker
                columns={dealColumns}
                hiddenIds={controls.hiddenColumnIds}
                onChange={controls.setHiddenColumnIds}
                requiredIds={["title"]}
              />
              <ExportMenu
                filename={`deals-${new Date().toISOString().slice(0, 10)}`}
                headers={DEAL_EXPORT_HEADERS}
                rows={filteredDeals.map(dealExportRow)}
              />
              <DensityMenu value={controls.density} onChange={controls.setDensity} />
              <span className="hidden text-xs text-slate-400 xl:block">
                Filtered <strong className="text-slate-700">{filteredDeals.length}</strong> of {deals.length}
              </span>
            </div>
          </div>
        </CardContent>
      </Card>

      <DataTable
        label="Deals"
        rows={paginatedDeals}
        columns={controls.visibleColumns}
        rowId={(deal) => deal.id}
        density={controls.density}
        sortBy={controls.sortBy}
        sortDir={controls.sortDir}
        onSortChange={controls.onSortChange}
        selectedIds={controls.selectedIds}
        onSelectionChange={controls.setSelectedIds}
        bulkActions={
          <ExportMenu
            filename={`deals-selected-${new Date().toISOString().slice(0, 10)}`}
            headers={DEAL_EXPORT_HEADERS}
            rows={paginatedDeals.filter((d) => controls.selectedIds.has(d.id)).map(dealExportRow)}
          />
        }
        isFiltered={filteredDeals.length !== deals.length}
        emptyTitle="No deals yet"
        emptyMessage="Qualify a lead and the deal will appear here."
        emptyAction={{ label: "New Deal", onClick: () => setIsNewDealDrawerOpen(true) }}
      />

      {/* Pagination Controls */}
      <div className="flex flex-col sm:flex-row justify-between items-center gap-4 bg-white px-6 py-4 rounded-xl border border-slate-100 shadow-sm text-xs">
        <div className="text-slate-500 font-medium">
          Showing <strong className="text-slate-700">{filteredDeals.length === 0 ? 0 : (currentPage - 1) * pageSize + 1}</strong> to{" "}
          <strong className="text-slate-700">
            {Math.min(currentPage * pageSize, filteredDeals.length)}
          </strong>{" "}
          of <strong className="text-slate-700">{filteredDeals.length}</strong> entries
        </div>
        <div className="flex items-center gap-1.5">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setCurrentPage(prev => Math.max(prev - 1, 1))}
            disabled={currentPage === 1}
            className="border-slate-200 text-slate-600 font-bold px-3 py-1.5 h-8 disabled:opacity-50"
          >
            Previous
          </Button>
          <div className="flex items-center gap-1">
            {Array.from({ length: totalPages }).map((_, idx) => {
              const p = idx + 1;
              const isCurrent = p === currentPage;
              return (
                <button
                  key={p}
                  onClick={() => setCurrentPage(p)}
                  className={`size-8 rounded-lg font-bold transition flex items-center justify-center ${isCurrent
                    ? "bg-[#185FA5] text-white shadow-xs"
                    : "text-slate-600 hover:bg-slate-100"
                    }`}
                >
                  {p}
                </button>
              );
            })}
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setCurrentPage(prev => Math.min(prev + 1, totalPages))}
            disabled={currentPage === totalPages}
            className="border-slate-200 text-slate-600 font-bold px-3 py-1.5 h-8 disabled:opacity-50"
          >
            Next
          </Button>
        </div>
      </div>

      {/* Slide-over Drawer for adding Deal */}
      {isNewDealDrawerOpen && (
        <Portal>
          {/* Backdrop */}
          <div
            className="fixed inset-0 bg-slate-900/30 backdrop-blur-xs z-40 transition-opacity"
            onClick={() => setIsNewDealDrawerOpen(false)}
          />
          {/* Drawer Element */}
          <div className="fixed inset-y-0 right-0 w-full max-w-md bg-white shadow-2xl border-l border-slate-200 z-50 flex flex-col animate-in slide-in-from-right duration-300">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-slate-50/50">
              <div>
                <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
                  <Briefcase className="size-4.5 text-[#185FA5]" />
                  Add New Sales Deal
                </h3>
                <p className="text-[10px] text-slate-400 mt-0.5">Move qualified leads to deal workflow to forecast booking sales</p>
              </div>
              <button
                onClick={() => setIsNewDealDrawerOpen(false)}
                className="p-1 rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
              >
                <X className="size-4.5" />
              </button>
            </div>

            {/* Form */}
            <form onSubmit={handleCreateDeal} className="flex-1 overflow-y-auto p-6 space-y-4">
              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Deal Title *</label>
                <Input maxLength={50}
                  required
                  placeholder="e.g. Wedding Catering Block, Corporate Conference..."
                  value={newDeal.title}
                  onChange={e => setNewDeal({ ...newDeal, title: e.target.value })}
                  className="py-1.5 text-xs focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white"
                />
              </div>

              <div className="space-y-1 relative">
                <label className="text-xs font-semibold text-slate-600">Select Customer *</label>
                {selectedCustomer ? (
                  <div className="flex items-center justify-between p-2 border border-[#85B7EB] bg-[#E6F1FB] rounded-md text-xs">
                    <div className="min-w-0 flex-1">
                      <div className="font-bold text-[#0C447C] truncate">{selectedCustomer.name}</div>
                      <div className="text-[10px] text-[#185FA5] truncate">
                        {[selectedCustomer.email, selectedCustomer.phone, selectedCustomer.company].filter(Boolean).join(" · ") || "No contact details"}
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedCustomer(null);
                        setNewDeal(prev => ({
                          ...prev,
                          customerId: "",
                          contactName: "",
                          email: "",
                          phone: ""
                        }));
                      }}
                      className="text-slate-400 hover:text-slate-600 p-1 shrink-0 ml-2"
                    >
                      <X className="size-4" />
                    </button>
                  </div>
                ) : (
                  <>
                    <div className="relative">
                      <input
                        type="text"
                        placeholder="Search customer by name or email..."
                        value={customerSearchQuery}
                        onChange={e => {
                          setCustomerSearchQuery(e.target.value);
                          setShowCustomerDropdown(true);
                        }}
                        onFocus={() => setShowCustomerDropdown(true)}
                        onBlur={() => setTimeout(() => setShowCustomerDropdown(false), 200)}
                        className="w-full p-2 text-xs border border-slate-200 rounded-md focus:outline-none focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white transition"
                      />
                    </div>
                    {showCustomerDropdown && (
                      <div className="absolute left-0 right-0 z-50 mt-1 max-h-48 overflow-y-auto rounded-md border border-slate-200 bg-white shadow-lg">
                        {isSearchingCustomers ? (
                          <div className="p-2.5 text-xs text-slate-400 text-center">Searching...</div>
                        ) : customerResults.length === 0 ? (
                          <div className="p-2.5 text-xs text-slate-400 text-center">No customers found</div>
                        ) : (
                          customerResults.map(c => (
                            <button
                              key={c.id}
                              type="button"
                              onMouseDown={() => {
                                setSelectedCustomer(c);
                                setNewDeal(prev => ({
                                  ...prev,
                                  customerId: c.id,
                                  contactName: c.name || "",
                                  email: c.email || "",
                                  phone: c.phone || ""
                                }));
                                setCustomerSearchQuery("");
                                setShowCustomerDropdown(false);
                              }}
                              className="w-full text-left p-2.5 text-xs hover:bg-[#E6F1FB] border-b border-slate-50 last:border-0 transition"
                            >
                              <div className="font-semibold text-slate-800">{c.name}</div>
                              <div className="text-[10px] text-slate-400">
                                {[c.email, c.phone, c.company].filter(Boolean).join(" · ")}
                              </div>
                            </button>
                          ))
                        )}
                      </div>
                    )}
                  </>
                )}
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Primary Contact Person</label>
                <Input
                  disabled
                  placeholder="Will be auto-filled from selected customer"
                  value={newDeal.contactName}
                  className="py-1.5 text-xs bg-slate-50 border-slate-200 text-slate-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Email Address</label>
                  <Input
                    disabled
                    placeholder="Will be auto-filled"
                    value={newDeal.email}
                    className="py-1.5 text-xs bg-slate-50 border-slate-200 text-slate-500"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Phone Number</label>
                  <Input
                    disabled
                    placeholder="Will be auto-filled"
                    value={newDeal.phone}
                    className="py-1.5 text-xs bg-slate-50 border-slate-200 text-slate-500"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Deal Value (VND)</label>
                  <Input
                    type="number"
                    placeholder="e.g. 15000"
                    value={newDeal.value}
                    onChange={e => setNewDeal({ ...newDeal, value: e.target.value })}
                    className="py-1.5 text-xs focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Initial Sales Stage</label>
                  <Select
                    value={newDeal.stage}
                    onChange={e => setNewDeal({ ...newDeal, stage: e.target.value as Deal["stage"] })}
                    className="py-1.5 focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white"
                  >
                    <option value="Inquiry">Inquiry</option>
                    <option value="Qualification">Qualification</option>
                    <option value="Proposal">Proposal</option>
                    <option value="Negotiation">Negotiation</option>
                    <option value="Contract">Contract</option>
                    <option value="Confirmed">Confirmed</option>
                  </Select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Probability % (0-100)</label>
                  <Input
                    type="number"
                    min="0"
                    max="100"
                    placeholder="50"
                    value={newDeal.probability}
                    onChange={e => setNewDeal({ ...newDeal, probability: e.target.value })}
                    className="py-1.5 text-xs focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Est. Close Date</label>
                  <Input
                    type="date"
                    value={newDeal.expectedClose}
                    onChange={e => setNewDeal({ ...newDeal, expectedClose: e.target.value })}
                    className="py-1.5 text-xs focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white"
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Deal Owner (Assignee)</label>
                <UserSelect
                  users={users}
                  value={newDeal.owner}
                  disabled={!isManager}
                  onChange={(email) => setNewDeal({ ...newDeal, owner: email })}
                />
                {/* BR-18 — ownership is a manager decision. Saying so beats a
                    greyed-out picker that looks broken to a Sales user. */}
                {!isManager && (
                  <BlockedHint reason="Only a Sales Manager can assign deal ownership (BR-18). This deal will be created under your name." />
                )}
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Notes / Details</label>
                <textarea
                  placeholder="Describe deal requirements, guest details, etc..."
                  value={newDeal.notes}
                  onChange={e => setNewDeal({ ...newDeal, notes: e.target.value })}
                  className="w-full min-h-20 p-2 text-xs border border-slate-200 rounded-md focus:outline-none focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white transition"
                />
              </div>

              <div className="pt-4 flex gap-3 border-t border-slate-100">
                <Button
                  type="submit"
                  variant="primary"
                  className="w-full text-xs py-2"
                >
                  Create active Deal
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => setIsNewDealDrawerOpen(false)}
                  className="w-full text-xs py-2"
                >
                  Cancel
                </Button>
              </div>
            </form>
          </div>
        </Portal>
      )}

      {/* Slide-over Drawer for editing/viewing Deal Detail */}
      {isEditDealDrawerOpen && editingDeal && (
        <Portal>
          {/* Backdrop */}
          <div
            className="fixed inset-0 bg-slate-900/30 backdrop-blur-xs z-40 transition-opacity"
            onClick={() => {
              setIsEditDealDrawerOpen(false);
              setEditingDeal(null);
            }}
          />
          {/* Drawer Element */}
          <div className="fixed inset-y-0 right-0 w-full max-w-md bg-white shadow-2xl border-l border-slate-200 z-50 flex flex-col animate-in slide-in-from-right duration-300">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-slate-50/50">
              <div>
                <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
                  <Briefcase className="size-4.5 text-[#185FA5]" />
                  Deal Details & Edit
                </h3>
                <p className="text-[10px] text-slate-400 mt-0.5">View and update sales deal size, forecast close date, and pipeline stage</p>
              </div>
              <button
                onClick={() => {
                  setIsEditDealDrawerOpen(false);
                  setEditingDeal(null);
                }}
                className="p-1 rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
              >
                <X className="size-4.5" />
              </button>
            </div>

            {/* Form */}
            <form onSubmit={handleUpdateDeal} className="flex-1 overflow-y-auto p-6 space-y-4">
              {isAlreadyClosed && (
                <div className="bg-amber-50 border border-amber-200 text-amber-800 px-4 py-2.5 rounded-lg text-xs font-semibold flex items-center gap-2 mb-4 animate-in fade-in duration-255">
                  <AlertCircle className="size-4 shrink-0 text-amber-600" />
                  <span>This deal is closed and cannot be modified.</span>
                </div>
              )}

              {/* Deal Workflow Stepper Progress Indicator */}
              <DealWorkflowStepper dealId={editingDeal.id} />

              {/* Stage Tracker Stepper */}
              <div className="space-y-2 border-b border-slate-100 pb-4 mb-4">
                <label className="text-xs font-bold text-slate-500 uppercase tracking-wider">Pipeline Stage Progression</label>
                <div className="flex items-center justify-between gap-1 mt-2">
                  {STAGES_ORDER.map((stg, idx) => {
                    const isCurrent = editingDeal.stage === stg;
                    const isPast = STAGES_ORDER.indexOf(editingDeal.stage) > idx;
                    const isDisabled = isAlreadyClosed;
                    return (
                      <button
                        key={stg}
                        type="button"
                        onClick={() => !isDisabled && handleStageClick(stg)}
                        disabled={isDisabled}
                        className={`flex-1 text-center py-1.5 px-0.5 rounded text-[9px] font-bold transition-all duration-200 border ${isCurrent
                          ? "bg-[#185FA5] border-[#185FA5] text-white shadow-xs"
                          : isPast
                            ? "bg-emerald-50 border-emerald-100 text-emerald-700 hover:bg-emerald-100"
                            : "bg-slate-50 border-slate-200 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
                          } ${isDisabled ? "cursor-not-allowed opacity-80" : ""}`}
                      >
                        {stg}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Deal Title *</label>
                <Input maxLength={50}
                  required
                  disabled={isAlreadyClosed}
                  placeholder="e.g. Wedding Catering Block, Corporate Conference..."
                  value={editingDeal.title || ""}
                  onChange={e => setEditingDeal({ ...editingDeal, title: e.target.value })}
                  className="py-1.5 text-xs focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white"
                />
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Primary Contact Person *</label>
                <Input
                  required
                  disabled={isAlreadyClosed}
                  placeholder="e.g. Alice Jenkins"
                  value={editingDeal.contactName || ""}
                  onChange={e => setEditingDeal({ ...editingDeal, contactName: e.target.value })}
                  className="py-1.5 text-xs focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Email Address</label>
                  <Input
                    type="email"
                    disabled={isAlreadyClosed}
                    placeholder="contact@gmail.com"
                    value={editingDeal.email || ""}
                    onChange={e => setEditingDeal({ ...editingDeal, email: e.target.value })}
                    className="py-1.5 text-xs focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Phone Number</label>
                  <Input
                    disabled={isAlreadyClosed}
                    placeholder="+1 555-0100"
                    value={editingDeal.phone || ""}
                    onChange={e => setEditingDeal({ ...editingDeal, phone: e.target.value })}
                    className="py-1.5 text-xs focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Deal Value (VND)</label>
                  <Input
                    type="number"
                    disabled={isAlreadyClosed}
                    placeholder="e.g. 15000"
                    value={editingDeal.value || ""}
                    onChange={e => setEditingDeal({ ...editingDeal, value: Number(e.target.value) })}
                    className="py-1.5 text-xs focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Sales Stage</label>
                  <Select
                    value={editingDeal.stage || "Inquiry"}
                    disabled={isAlreadyClosed}
                    onChange={e => setEditingDeal({ ...editingDeal, stage: e.target.value as Deal["stage"] })}
                    className="py-1.5 focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white"
                  >
                    <option value="Inquiry">Inquiry</option>
                    <option value="Qualification">Qualification</option>
                    <option value="Proposal">Proposal</option>
                    <option value="Negotiation">Negotiation</option>
                    <option value="Contract">Contract</option>
                    <option value="Confirmed">Confirmed</option>
                  </Select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Probability % (0-100)</label>
                  <Input
                    type="number"
                    min="0"
                    max="100"
                    disabled={isAlreadyClosed}
                    placeholder="50"
                    value={editingDeal.probability || 0}
                    onChange={e => setEditingDeal({ ...editingDeal, probability: Number(e.target.value) })}
                    className="py-1.5 text-xs focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Est. Close Date</label>
                  <Input
                    type="date"
                    disabled={isAlreadyClosed}
                    value={editingDeal.expectedClose || ""}
                    onChange={e => setEditingDeal({ ...editingDeal, expectedClose: e.target.value })}
                    className="py-1.5 text-xs focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Status</label>
                  <Select
                    value={editingDeal.status || "active"}
                    disabled={isAlreadyClosed}
                    onChange={e => {
                      const newStatus = e.target.value as Deal["status"];
                      let newStage = editingDeal.stage;
                      if (newStatus === "won") {
                        newStage = "Confirmed";
                      } else if (newStatus === "active" && editingDeal.stage === "Confirmed") {
                        newStage = "Contract";
                      }
                      setEditingDeal({ ...editingDeal, status: newStatus, stage: newStage });
                    }}
                    className="py-1.5 focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white"
                  >
                    <option value="active">Active</option>
                    <option value="won">Won</option>
                    <option value="lost">Lost</option>
                  </Select>
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Owner</label>
                  <UserSelect
                    users={users}
                    value={editingDeal.ownerEmail || ""}
                    disabled={isAlreadyClosed || !isManager}
                    onChange={(email, fullName) => setEditingDeal({
                      ...editingDeal,
                      ownerEmail: email,
                      owner: fullName
                    })}
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Notes / Details</label>
                <textarea
                  placeholder="Describe deal requirements, guest details, notes, etc..."
                  value={editingDeal.notes || ""}
                  disabled={isAlreadyClosed}
                  onChange={e => setEditingDeal({ ...editingDeal, notes: e.target.value })}
                  className="w-full min-h-25 p-2 text-xs border border-slate-200 rounded-md focus:outline-none focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white disabled:bg-slate-50 disabled:text-slate-400 transition"
                />
              </div>

              <div className="pt-4 flex gap-3 border-t border-slate-100">
                {!isAlreadyClosed ? (
                  <>
                    <Button
                      type="submit"
                      variant="primary"
                      className="w-full text-xs py-2"
                    >
                      Save Changes
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      onClick={() => {
                        setIsEditDealDrawerOpen(false);
                        setEditingDeal(null);
                      }}
                      className="w-full text-xs py-2"
                    >
                      Cancel
                    </Button>
                  </>
                ) : (
                  <Button
                    type="button"
                    variant="primary"
                    onClick={() => {
                      setIsEditDealDrawerOpen(false);
                      setEditingDeal(null);
                    }}
                    className="w-full text-xs py-2 bg-slate-600 hover:bg-slate-700"
                  >
                    Close Details
                  </Button>
                )}
              </div>
            </form>
          </div>
        </Portal>
      )}
    </div>
  );
}
