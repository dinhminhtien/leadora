"use client";

/**
 * The morning reconciliation: bringing the CRM's allotment back in line with the hotel.
 *
 * <p>This is what replaces "have Reservation update the system after every checkout". Doing it
 * per guest movement recreates the person-waiting-on-person loop the whole feature exists to
 * remove — just relocated to the end of the process. Once a day over a short window costs one
 * screen instead of one message per booking.
 *
 * <p>The desk enters only what they can read off the hotel's system: rooms still free. The
 * server adds back what has already been sold to recover the block size. Making a person do
 * that subtraction on every row is how a correction screen ends up less accurate than the drift
 * it was built to fix.
 */

import * as React from "react";

import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { LoadingState } from "@/shared/components/LoadingState";
import { apiErrorMessage } from "@/services/api_error";
import type { ReconcileEntry } from "@/services/room_availability_service";
import { addDays, todayIso } from "@/shared/utils/calendar_date";
import { toast } from "@/stores/toast_store";
import {
  useAvailabilityGrid,
  useReconcileAllotment,
} from "@/features/room_availability/hooks/use_room_availability";

/** A week is what a desk can realistically eyeball against a PMS in one sitting. */
const DEFAULT_DAYS = 7;

type Row = {
  key: string;
  productId: string;
  roomType: string;
  date: string;
  allotted?: number;
  booked: number;
  held: number;
  /**
   * What the hotel's own system should be showing for our block: the allocation less what we
   * have entered into their system as sold.
   *
   * Deliberately **not** the grid's `available`. That figure also nets off our holds, which are
   * internal to this CRM — the hotel has never been told about them, so a held room still counts
   * as free on their screen. Comparing the desk's reading against `available` made the panel
   * disagree with the server it feeds: the server reconstructs the block as
   * `observed + booked`, so a desk that typed the CRM's own number would silently shrink the
   * allocation by every held room, and a desk that typed the hotel's true figure would see a
   * "mismatch" the save then reported as no change.
   */
  comparable?: number;
  published: boolean;
  closed: boolean;
  stale: boolean;
};

export function ReconciliationPanel() {
  const [from, setFrom] = React.useState(todayIso);
  const [days, setDays] = React.useState(DEFAULT_DAYS);
  const [observed, setObserved] = React.useState<Record<string, string>>({});
  const [error, setError] = React.useState<string | null>(null);

  const to = addDays(from, days - 1);
  const { data: grid, isLoading } = useAvailabilityGrid({ from, to });
  const reconcile = useReconcileAllotment();

  const rows: Row[] = React.useMemo(() => {
    const flat: Row[] = [];
    for (const room of grid?.rooms ?? []) {
      for (const day of room.days) {
        // Stop-sell nights are not a drift to correct — the hotel has closed them to us, and
        // reopening one is a decision, not a reconciliation.
        if (day.closed) continue;
        flat.push({
          key: `${room.productId}|${day.date}`,
          productId: room.productId,
          roomType: room.roomType,
          date: day.date,
          allotted: day.allotted,
          booked: day.booked,
          held: day.held,
          comparable: day.allotted === undefined ? undefined : Math.max(0, day.allotted - day.booked),
          published: day.status === "PUBLISHED",
          closed: day.closed,
          stale: day.stale,
        });
      }
    }
    return flat;
  }, [grid]);

  const entries: ReconcileEntry[] = React.useMemo(
    () =>
      rows
        .map((row) => ({ row, raw: observed[row.key] }))
        .filter(({ raw }) => raw !== undefined && raw !== "")
        .map(({ row, raw }) => ({
          productId: row.productId,
          stayDate: row.date,
          actualAvailable: Number(raw),
        }))
        .filter((entry) => Number.isFinite(entry.actualAvailable) && entry.actualAvailable >= 0),
    [rows, observed],
  );

  const handleSubmit = async () => {
    setError(null);
    try {
      const response = await reconcile.mutateAsync({ entries });
      const result = response.data;
      setObserved({});
      if (result.nightsChanged === 0) {
        toast.success(`Reconciled ${result.nightsReconciled} night(s) — everything already matched`);
      } else {
        toast.success(
          `Reconciled ${result.nightsReconciled} night(s); ${result.nightsChanged} corrected`,
        );
      }
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <div className="space-y-3">
      <div className="rounded-lg bg-slate-50 px-3 py-2.5 text-[12px] text-muted-foreground">
        Open the hotel&apos;s system and enter, for each night, how many rooms of ours are still
        free <em>there</em>. Leave a row blank if you have not checked it. The block size is
        worked out for you — rooms already sold are added back, so you never have to do the
        arithmetic. Rooms we are only holding against a quotation still show as free on the
        hotel&apos;s side, so count them as free here too; the <strong>Expected there</strong>
        column is the figure your reading should match.
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <label className="text-[13px] font-medium">From</label>
        <div className="w-40">
          <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <label className="text-[13px] font-medium">Nights</label>
        <div className="w-24">
          <Input
            type="number"
            min={1}
            max={30}
            value={days}
            onChange={(e) => setDays(Math.min(30, Math.max(1, Number(e.target.value) || DEFAULT_DAYS)))}
          />
        </div>
        <Button variant="ghost" onClick={() => setFrom(todayIso())}>
          Today
        </Button>

        <div className="ml-auto flex items-center gap-2">
          <span className="text-[12px] text-muted-foreground">
            {entries.length} night(s) entered
          </span>
          <Button onClick={handleSubmit} disabled={entries.length === 0 || reconcile.isPending}>
            {reconcile.isPending ? "Saving..." : "Confirm reconciliation"}
          </Button>
        </div>
      </div>

      {error && <p className="rounded-md bg-red-50 px-3 py-2 text-[13px] text-red-700">{error}</p>}

      {isLoading && <LoadingState label="Loading allotment..." />}

      {!isLoading && rows.length === 0 && (
        <p className="rounded-lg border border-dashed border-slate-200 px-4 py-8 text-center text-[13px] text-muted-foreground">
          Nothing to reconcile in this window.
        </p>
      )}

      {!isLoading && rows.length > 0 && (
        <div className="overflow-x-auto rounded-lg border border-slate-200">
          <table className="w-full border-collapse text-[13px]">
            <thead>
              <tr className="bg-slate-50 text-left">
                <th className="px-3 py-2 font-medium">Room type</th>
                <th className="px-3 py-2 font-medium">Night</th>
                <th className="px-3 py-2 text-right font-medium">Allocated</th>
                <th className="px-3 py-2 text-right font-medium">Sold</th>
                <th className="px-3 py-2 text-right font-medium" title="Rooms we are holding against open quotations. The hotel has not been told, so these still show as free on their side.">
                  Held
                </th>
                <th className="px-3 py-2 text-right font-medium" title="What the hotel's system should be showing for our block: allocated less what we have entered there as sold. Holds are not subtracted — the hotel does not know about them.">
                  Expected there
                </th>
                <th className="px-3 py-2 text-right font-medium">Free (hotel)</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => {
                const raw = observed[row.key];
                const entered = raw !== undefined && raw !== "";
                // Highlight only where the desk has actually contradicted the CRM. Flagging
                // every row would make the signal worthless on a screen that is mostly matches.
                const mismatch =
                  entered && row.comparable !== undefined && Number(raw) !== row.comparable;

                return (
                  <tr
                    key={row.key}
                    className={`border-t border-slate-100 ${mismatch ? "bg-amber-50" : ""}`}
                  >
                    <td className="px-3 py-1.5">{row.roomType}</td>
                    <td className="px-3 py-1.5">
                      {row.date}
                      {row.stale && (
                        <span className="ml-1.5 text-[11px] text-amber-700">not recently checked</span>
                      )}
                    </td>
                    <td className="px-3 py-1.5 text-right">
                      {row.published ? row.allotted : <span className="text-slate-300">—</span>}
                    </td>
                    <td className="px-3 py-1.5 text-right">{row.booked}</td>
                    <td className="px-3 py-1.5 text-right text-muted-foreground">
                      {row.held > 0 ? row.held : <span className="text-slate-300">—</span>}
                    </td>
                    <td className="px-3 py-1.5 text-right">
                      {row.published ? row.comparable : <span className="text-slate-300">—</span>}
                    </td>
                    <td className="px-3 py-1.5 text-right">
                      <div className="ml-auto w-24">
                        <Input
                          type="number"
                          min={0}
                          value={raw ?? ""}
                          placeholder="—"
                          onChange={(e) =>
                            setObserved((current) => ({ ...current, [row.key]: e.target.value }))
                          }
                        />
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
