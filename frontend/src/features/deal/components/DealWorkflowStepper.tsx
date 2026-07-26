import React, { useEffect, useState } from "react";
import { dealService, type DealWorkflowSummaryResponse } from "@/services/deal_service";
import { CheckCircle2, Circle, Loader2, AlertCircle, DollarSign, FileText, Calendar, ShieldCheck } from "lucide-react";
import { Badge } from "@/components/ui/Badge";

interface DealWorkflowStepperProps {
  dealId: string;
}

/// Wire stage name → the label used everywhere else in the product.
const STAGE_LABELS: Record<string, string> = {
  PROSPECTING: "New",
  QUALIFICATION: "Qualified",
  PROPOSAL: "Proposal",
  NEGOTIATION: "Negotiation",
  CLOSED_WON: "Won",
  CLOSED_LOST: "Lost",
};

export const DealWorkflowStepper: React.FC<DealWorkflowStepperProps> = ({ dealId }) => {
  const [summary, setSummary] = useState<DealWorkflowSummaryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const fetchSummary = async () => {
      setLoading(true);
      setError(null);
      try {
        const response = await dealService.getWorkflowSummary(dealId);
        if (active) {
          if (response && response.success && response.data) {
            setSummary(response.data);
          } else {
            setError(response?.message || "Failed to retrieve workflow summary");
          }
        }
      } catch (err: any) {
        if (active) {
          console.error("Error loading deal workflow summary", err);
          setError(err.response?.data?.message || err.message || "An error occurred");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    if (dealId) {
      fetchSummary();
    }
    return () => {
      active = false;
    };
  }, [dealId]);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-6 border border-slate-100 bg-slate-50/30 rounded-lg">
        <Loader2 className="size-5 text-[#185FA5] animate-spin mr-2" />
        <span className="text-xs text-slate-500 font-bold">Loading sales workflow progress...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-3 bg-red-50 border border-red-200 text-red-800 rounded-lg text-xs font-semibold flex items-center gap-2">
        <AlertCircle className="size-4 shrink-0 text-red-600" />
        <span>{error}</span>
      </div>
    );
  }

  if (!summary) return null;

  // `dealStatus` and `pipelineStage` arrive as the raw enum names (OPEN/WON/LOST and
  // PROSPECTING/QUALIFICATION/PROPOSAL/NEGOTIATION/CLOSED_WON/CLOSED_LOST) because
  // GetDealWorkflowSummaryUseCase serializes `.name()`. Note this differs from
  // DealResponse.status, which the mapper lowercases — compare against the names here.
  const dealStatus = summary.dealStatus?.toUpperCase();
  const stage = summary.pipelineStage?.toUpperCase();
  const isClosed = dealStatus === "WON" || dealStatus === "LOST";

  const steps = [
    {
      title: "Inquiry Stage",
      description: "Initial client inquiry registered",
      isCompleted: true, // Inquiry is always completed once deal is active
      isActive: !isClosed && (stage === "PROSPECTING" || stage === "QUALIFICATION"),
      meta: (
        <span className="text-[10px] text-slate-400 font-medium">
          Stage: {STAGE_LABELS[stage ?? ""] ?? summary.pipelineStage ?? "—"}
        </span>
      ),
    },
    {
      title: "Proposal / Quotation",
      description: "Send pricing options & get approval",
      isCompleted: !!summary.activeQuotationId,
      isActive: !isClosed && stage === "PROPOSAL",
      meta: summary.activeQuotationId ? (
        <div className="flex items-center gap-1.5 mt-1 bg-blue-50/60 border border-blue-100 px-2 py-0.5 rounded text-[10px] text-blue-700 w-fit">
          <FileText className="size-3" />
          <span className="font-bold truncate">Q: {summary.activeQuotationId.slice(0, 8)}</span>
          <Badge className="text-[8px] px-1 py-0 bg-blue-100 border-0 text-blue-800 font-bold uppercase">
            {summary.activeQuotationStatus}
          </Badge>
        </div>
      ) : (
        <span className="text-[10px] text-slate-400 italic">No Active Quotation</span>
      ),
    },
    {
      title: "Booking Reservation",
      description: "Confirm details & reserve resources",
      isCompleted: !!summary.activeBookingId,
      isActive: !isClosed && stage === "NEGOTIATION",
      meta: summary.activeBookingId ? (
        <div className="flex items-center gap-1.5 mt-1 bg-indigo-50/60 border border-indigo-100 px-2 py-0.5 rounded text-[10px] text-indigo-700 w-fit">
          <Calendar className="size-3" />
          <span className="font-bold truncate">B: {summary.activeBookingId.slice(0, 8)}</span>
          <Badge className="text-[8px] px-1 py-0 bg-indigo-100 border-0 text-indigo-800 font-bold uppercase">
            {summary.activeBookingStatus}
          </Badge>
        </div>
      ) : (
        <span className="text-[10px] text-slate-400 italic">No Active Booking</span>
      ),
    },
    {
      title: "Securing Payment",
      description: "Require deposit or full payment",
      isCompleted: summary.hasPaidPayment,
      isActive: !isClosed && !!summary.activeBookingId && !summary.hasPaidPayment,
      meta: summary.currentPaymentStatus ? (
        <div className={`flex items-center gap-1.5 mt-1 border px-2 py-0.5 rounded text-[10px] w-fit font-bold ${
          summary.hasPaidPayment 
            ? "bg-emerald-50 border-emerald-100 text-emerald-700" 
            : "bg-amber-50 border-amber-100 text-amber-700"
        }`}>
          <DollarSign className="size-3" />
          <span>Payment: {summary.currentPaymentStatus}</span>
        </div>
      ) : (
        <span className="text-[10px] text-slate-400 italic">No payments recorded</span>
      ),
    },
    {
      title: "Deal Closed Won",
      description: "Contract signed, booking paid, sales complete",
      isCompleted: dealStatus === "WON",
      // The stage can sit on CLOSED_WON before the deal itself is marked won.
      isActive: !isClosed && stage === "CLOSED_WON",
      meta: (
        <div className="flex items-center gap-1.5 mt-1">
          <Badge variant={dealStatus === "WON" ? "success" : dealStatus === "LOST" ? "danger" : "default"} className="text-[8px] font-bold uppercase">
            {summary.dealStatus ?? "—"}
          </Badge>
        </div>
      ),
    },
  ];

  return (
    <div className="bg-slate-50/50 rounded-xl p-4 border border-slate-100/80 space-y-3">
      <div className="flex items-center justify-between border-b border-slate-100 pb-2">
        <h4 className="text-xs font-bold text-slate-700 flex items-center gap-1.5">
          <ShieldCheck className="size-4 text-[#185FA5]" />
          Sales Lifecycle Workflow
        </h4>
        <span className="text-[9px] font-black uppercase text-[#185FA5] bg-blue-50 px-1.5 py-0.5 rounded border border-blue-100/50">
          Sync Active
        </span>
      </div>

      <div className="relative pl-6 space-y-4">
        {/* Connection timeline line */}
        <div className="absolute left-2.5 top-2 bottom-2 w-0.5 bg-slate-200" />

        {steps.map((step, idx) => {
          return (
            <div key={idx} className="relative flex flex-col gap-0.5">
              {/* Stepper icon/marker */}
              <div className="absolute -left-6.5 top-0.5 bg-white rounded-full">
                {step.isCompleted ? (
                  <CheckCircle2 className="size-5 text-emerald-500 fill-white" />
                ) : step.isActive ? (
                  <Loader2 className="size-5 text-[#185FA5] animate-spin fill-white" />
                ) : (
                  <Circle className="size-5 text-slate-300 fill-white" />
                )}
              </div>

              {/* Title & status */}
              <div className="flex items-center justify-between gap-2">
                <span className={`text-xs font-bold ${
                  step.isCompleted 
                    ? "text-slate-800" 
                    : step.isActive 
                    ? "text-[#185FA5] font-black" 
                    : "text-slate-400"
                }`}>
                  {step.title}
                </span>
                {step.isCompleted && (
                  <span className="text-[8px] font-bold text-emerald-600 bg-emerald-50 border border-emerald-100 px-1 py-0.2 rounded uppercase scale-90">
                    Done
                  </span>
                )}
              </div>

              {/* Description */}
              <p className="text-[10px] text-slate-400 font-medium leading-normal">
                {step.description}
              </p>

              {/* Step Meta (active quotation/booking ids, statuses) */}
              <div className="mt-1">
                {step.meta}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
