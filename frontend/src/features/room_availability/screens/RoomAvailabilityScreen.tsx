"use client";

/**
 * The room allotment grid — room types down, nights across.
 *
 * This screen is the answer to a conversation. Until now "is a Deluxe free on the 20th?" could
 * only be settled by messaging the Reservation team, who opened the hotel's real PMS and replied
 * some minutes later, for a question that recurs dozens of times a day.
 *
 * What is shown is **quota, not occupancy**: the block the hotel has released to us to sell. The
 * hotel does not tell us how full it is. So an empty cell means our allocation is spent, and the
 * Reservation team can often get more — the copy has to keep saying that, or the screen quietly
 * becomes a source of "the hotel is full" claims it has no basis for.
 */

import * as React from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, Plus, TriangleAlert } from "lucide-react";

import { PAGE_META } from "@/app/routes/page_meta";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/page-header";
import { Select } from "@/components/ui/Select";
import { LoadingState } from "@/shared/components/LoadingState";
import { apiErrorMessage } from "@/services/api_error";
import { productService } from "@/services/product_service";
import type { AvailabilityNight } from "@/services/room_availability_service";
import { getUserRole } from "@/shared/auth/access";
import { useAuthStore } from "@/stores/auth_store";
import { addDays, shortDateParts, todayIso } from "@/shared/utils/calendar_date";
import { useAvailabilityGrid } from "@/features/room_availability/hooks/use_room_availability";
import { PublishAllotmentDialog } from "@/features/room_availability/components/PublishAllotmentDialog";
import { ReconciliationPanel } from "@/features/room_availability/components/ReconciliationPanel";
import { ImportAllotmentButton } from "@/features/room_availability/components/ImportAllotmentButton";

/** Two weeks fits a screen without horizontal scrolling on a laptop. */
const WINDOW_DAYS = 14;

function formatAsOf(iso?: string): string | null {
  if (!iso) return null;
  const date = new Date(iso);
  return date.toLocaleString(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    day: "2-digit",
    month: "2-digit",
  });
}

export function RoomAvailabilityScreen() {
  const { user } = useAuthStore();
  const role = getUserRole(user);
  const canPublish = role === "RESERVATION" || role === "ADMIN";

  const [from, setFrom] = React.useState(todayIso);
  const [productFilter, setProductFilter] = React.useState("");
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [dialogSeed, setDialogSeed] = React.useState<{ productId?: string; date?: string }>({});
  const [tab, setTab] = React.useState<"grid" | "reconcile">("grid");

  const to = addDays(from, WINDOW_DAYS - 1);

  const { data: rooms = [] } = useQuery({
    queryKey: ["product-services", "ROOM"],
    queryFn: () => productService.getList("ROOM"),
    select: (res) => res.data ?? [],
    staleTime: 5 * 60 * 1000,
  });

  const { data: grid, isLoading, isError, error } = useAvailabilityGrid({
    from,
    to,
    productId: productFilter || undefined,
  });

  const openPublish = (seed: { productId?: string; date?: string }) => {
    if (!canPublish) return;
    setDialogSeed(seed);
    setDialogOpen(true);
  };

  const anyStale = (grid?.rooms ?? []).some((room) => room.days.some((day) => day.stale));
  const oldestAsOf = (grid?.rooms ?? [])
    .flatMap((room) => room.days.map((day) => day.asOf))
    .filter((value): value is string => !!value)
    .sort()[0];

  return (
    <div className="space-y-4">
      <PageHeader
        {...PAGE_META.roomAvailability}
        actions={
          canPublish ? (
            <div className="flex flex-wrap items-center justify-end gap-2">
              <ImportAllotmentButton />
              <Button onClick={() => openPublish({})}>
                <Plus className="mr-1.5 h-4 w-4" />
                Publish allotment
              </Button>
            </div>
          ) : undefined
        }
      />

      {/*
        The reconcile tab is Reservation's alone: it exists to record what the hotel's own system
        says, and no other role can read that. Sales seeing an empty version of it would only
        invite them to fill it in from guesswork.
      */}
      {canPublish && (
        <div className="flex gap-1 border-b border-slate-200">
          {(["grid", "reconcile"] as const).map((value) => (
            <button
              key={value}
              type="button"
              onClick={() => setTab(value)}
              className={`-mb-px border-b-2 px-3 py-2 text-[13px] transition ${
                tab === value
                  ? "border-slate-900 font-medium text-slate-900"
                  : "border-transparent text-slate-500 hover:text-slate-700"
              }`}
            >
              {value === "grid" ? "Allotment grid" : "Daily reconciliation"}
            </button>
          ))}
        </div>
      )}

      {canPublish && tab === "reconcile" ? (
        <ReconciliationPanel />
      ) : (
        <>
        {/*
          Staleness is not decoration. These numbers are keyed in by hand from a system this CRM
          cannot see, so the hotel can sell rooms through other channels without anything here
          changing. A figure nobody has re-confirmed today has to say so.
        */}
        {anyStale && (
          <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5 text-[13px] text-amber-900">
            <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" />
            <p>
              Some figures have not been re-confirmed recently
              {oldestAsOf ? ` (oldest: ${formatAsOf(oldestAsOf)})` : ""}. Treat them as indicative
              and confirm with the Reservation team before promising a room.
            </p>
          </div>
        )}

        <div className="flex flex-wrap items-center gap-2">
          <Button variant="ghost" onClick={() => setFrom(addDays(from, -WINDOW_DAYS))}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="text-[13px] font-medium">
            {from} → {to}
          </span>
          <Button variant="ghost" onClick={() => setFrom(addDays(from, WINDOW_DAYS))}>
            <ChevronRight className="h-4 w-4" />
          </Button>
          <Button variant="ghost" onClick={() => setFrom(todayIso())}>
            Today
          </Button>

          <div className="ml-auto w-56">
            <Select value={productFilter} onChange={(e) => setProductFilter(e.target.value)}>
              <option value="">All room types</option>
              {rooms.map((room) => (
                <option key={room.productId} value={room.productId}>
                  {room.name}
                </option>
              ))}
            </Select>
          </div>
        </div>

        {isLoading && <LoadingState label="Loading room allotment..." />}

        {isError && (
          <p className="rounded-md bg-red-50 px-3 py-2 text-[13px] text-red-700">
            {apiErrorMessage(error)}
          </p>
        )}

        {!isLoading && !isError && (grid?.rooms.length ?? 0) === 0 && (
          <p className="rounded-lg border border-dashed border-slate-200 px-4 py-8 text-center text-[13px] text-muted-foreground">
            No active room types found in the product catalogue.
          </p>
        )}

        {!isLoading && !isError && (grid?.rooms.length ?? 0) > 0 && (
          <div className="overflow-x-auto rounded-lg border border-slate-200">
            <table className="w-full border-collapse text-[13px]">
              <thead>
                <tr className="bg-slate-50">
                  <th className="sticky left-0 z-10 min-w-[180px] bg-slate-50 px-3 py-2 text-left font-medium">
                    Room type
                  </th>
                  {(grid?.rooms[0]?.days ?? []).map((day) => {
                    const label = shortDateParts(day.date);
                    return (
                      <th key={day.date} className="min-w-[64px] px-2 py-2 text-center font-medium">
                        <span className="block text-[11px] text-muted-foreground">{label.weekday}</span>
                        <span className="block">{label.day}</span>
                      </th>
                    );
                  })}
                </tr>
              </thead>
              <tbody>
                {(grid?.rooms ?? []).map((room) => (
                  <tr key={room.productId} className="border-t border-slate-100">
                    <td className="sticky left-0 z-10 bg-white px-3 py-2 font-medium">
                      {room.roomType}
                    </td>
                    {room.days.map((day) => (
                      <NightCell
                        key={day.date}
                        night={day}
                        canPublish={canPublish}
                        onClick={() => openPublish({ productId: room.productId, date: day.date })}
                      />
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <Legend canPublish={canPublish} />
        </>
      )}

      <PublishAllotmentDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        rooms={rooms}
        initial={dialogSeed}
      />
    </div>
  );
}

/**
 * One night for one room type.
 *
 * The three states are told apart by **symbol and text**, not colour alone: the grid is read
 * quickly, printed, and shared, and "sold out" versus "nobody has published this yet" is exactly
 * the distinction a colour-only cue loses. Treating an unpublished month as sold out would have
 * reps turning away business the hotel could service.
 */
function NightCell({
  night,
  canPublish,
  onClick,
}: {
  night: AvailabilityNight;
  canPublish: boolean;
  onClick: () => void;
}) {
  let content: React.ReactNode;
  let tone = "text-slate-900";
  let title: string;

  if (night.closed) {
    content = "✕";
    tone = "text-slate-400";
    title = "Stop-sell — the hotel has closed this date to us";
  } else if (night.status === "NOT_PUBLISHED") {
    content = "—";
    tone = "text-slate-300";
    title = "Not published yet — this is not the same as sold out";
  } else {
    const available = night.available ?? 0;
    content = available;
    tone = available === 0 ? "text-red-600 font-semibold" : "text-slate-900";
    title =
      `${available} of ${night.allotted} left · ${night.booked} booked · ${night.held} held` +
      (night.asOf ? ` · updated ${formatAsOf(night.asOf)}` : "") +
      (night.stale ? " · not recently confirmed" : "");
  }

  const Cell = canPublish ? "button" : "div";

  return (
    <td className="px-1 py-1 text-center">
      <Cell
        {...(canPublish ? { type: "button" as const, onClick } : {})}
        title={title}
        className={`flex h-9 w-full items-center justify-center rounded ${tone} ${
          canPublish ? "cursor-pointer hover:bg-slate-100" : ""
        } ${night.stale && night.status === "PUBLISHED" ? "underline decoration-amber-500 decoration-dotted underline-offset-4" : ""}`}
      >
        {content}
      </Cell>
    </td>
  );
}

function Legend({ canPublish }: { canPublish: boolean }) {
  return (
    <div className="space-y-1 rounded-lg bg-slate-50 px-3 py-2.5 text-[12px] text-muted-foreground">
      <p>
        <span className="font-medium text-slate-700">Numbers</span> are rooms left in the
        allocation the hotel released to us — not the hotel&apos;s own vacancy.{" "}
        <span className="font-medium text-slate-700">—</span> means not published yet, which is
        not the same as sold out. <span className="font-medium text-slate-700">✕</span> means
        stop-sell.
      </p>
      <p>
        Hover a cell for the full breakdown. A dotted underline marks figures nobody has
        re-confirmed recently.
      </p>
      {!canPublish && (
        // No "ask Reservation" control here on purpose: a room request is raised against a
        // quotation, and this screen has none. Quoting rooms the allocation cannot cover
        // raises the request automatically, which is where the ask belongs.
        <p>
          Short of rooms? Quote as usual — if the allocation cannot cover it, the Reservation
          team is asked automatically and the quotation is flagged until they answer.
        </p>
      )}
    </div>
  );
}
