"use client";

/**
 * The Reservation team publishing the hotel's allocation.
 *
 * Entered as a range because that is how hotels hand quota over — "ten Deluxe for the first
 * half of August" — while the server stores one row per night so a single date can later be
 * adjusted without splitting or merging anything.
 */

import * as React from "react";

import { Button } from "@/components/ui/Button";
import {
  Dialog,
  DialogBody,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { apiErrorMessage } from "@/services/api_error";
import type { ProductService } from "@/services/product_service";
import { toast } from "@/stores/toast_store";
import { usePublishAllotment } from "@/features/room_availability/hooks/use_room_availability";

const WEEKDAYS = [
  { value: "MONDAY", label: "Mon" },
  { value: "TUESDAY", label: "Tue" },
  { value: "WEDNESDAY", label: "Wed" },
  { value: "THURSDAY", label: "Thu" },
  { value: "FRIDAY", label: "Fri" },
  { value: "SATURDAY", label: "Sat" },
  { value: "SUNDAY", label: "Sun" },
] as const;

type PublishAllotmentDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  rooms: ProductService[];
  /** Pre-selects the cell the user clicked, so the form opens on the night they meant. */
  initial?: { productId?: string; date?: string };
};

export function PublishAllotmentDialog({ open, onOpenChange, rooms, initial }: PublishAllotmentDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {/* Body in its own component so Radix unmounting it on close resets the form. */}
      {open && (
        <PublishAllotmentBody onOpenChange={onOpenChange} rooms={rooms} initial={initial} />
      )}
    </Dialog>
  );
}

function PublishAllotmentBody({
  onOpenChange,
  rooms,
  initial,
}: Omit<PublishAllotmentDialogProps, "open">) {
  const publish = usePublishAllotment();

  const [productId, setProductId] = React.useState(initial?.productId ?? rooms[0]?.productId ?? "");
  const [dateFrom, setDateFrom] = React.useState(initial?.date ?? "");
  const [dateTo, setDateTo] = React.useState(initial?.date ?? "");
  const [allottedQty, setAllottedQty] = React.useState("0");
  const [closed, setClosed] = React.useState(false);
  const [note, setNote] = React.useState("");
  const [weekdays, setWeekdays] = React.useState<string[]>([]);
  const [error, setError] = React.useState<string | null>(null);

  const toggleWeekday = (value: string) =>
    setWeekdays((current) =>
      current.includes(value) ? current.filter((d) => d !== value) : [...current, value],
    );

  const rangeInvalid = !!dateFrom && !!dateTo && new Date(dateTo) < new Date(dateFrom);
  const quantity = Number(allottedQty);
  const canSubmit =
    !!productId && !!dateFrom && !!dateTo && !rangeInvalid && Number.isFinite(quantity) && quantity >= 0;

  const handleSubmit = async () => {
    setError(null);
    try {
      const response = await publish.mutateAsync({
        productId,
        dateFrom,
        dateTo,
        allottedQty: quantity,
        closed,
        note: note.trim() || undefined,
        weekdays: weekdays.length > 0 ? weekdays : undefined,
      });

      // The server publishes the figure even when it lands below what is already sold, and
      // hands back those nights. Reporting it as a plain success would hide an overbooking
      // the desk has just created and needs to renegotiate.
      const oversold = response.data ?? [];
      if (oversold.length > 0) {
        toast.warning(
          `Allotment saved, but ${oversold.length} night(s) are now below the rooms already sold. The assigned sales staff have been notified.`,
        );
      } else {
        toast.success("Room allotment updated");
      }
      onOpenChange(false);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <DialogContent size="md">
      <DialogHeader>
        <DialogTitle>Publish room allotment</DialogTitle>
        <DialogDescription>
          The number of rooms the hotel has released to us to sell — not the hotel&apos;s own
          occupancy. Saved one night at a time across the range.
        </DialogDescription>
      </DialogHeader>

      <DialogBody className="space-y-4">
        <div>
          <label className="mb-1 block text-[13px] font-medium">Room type</label>
          <Select value={productId} onChange={(e) => setProductId(e.target.value)}>
            <option value="">-- Select room type --</option>
            {rooms.map((room) => (
              <option key={room.productId} value={room.productId}>
                {room.name}
              </option>
            ))}
          </Select>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-[13px] font-medium">From</label>
            <Input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} />
          </div>
          <div>
            <label className="mb-1 block text-[13px] font-medium">To (inclusive)</label>
            <Input
              type="date"
              value={dateTo}
              onChange={(e) => setDateTo(e.target.value)}
              error={rangeInvalid ? "End date must not be before the start date" : undefined}
            />
          </div>
        </div>

        <div>
          <label className="mb-1 block text-[13px] font-medium">Rooms allocated per night</label>
          <Input
            type="number"
            min={0}
            value={allottedQty}
            onChange={(e) => setAllottedQty(e.target.value)}
            disabled={closed}
          />
          <p className="mt-1 text-[12px] text-muted-foreground">
            Zero means our allocation is used up — Sales can still ask you to extend it.
          </p>
        </div>

        <div className="rounded-lg border border-slate-200 p-3">
          <label className="flex items-start gap-2 text-[13px]">
            <input
              type="checkbox"
              className="mt-0.5"
              checked={closed}
              onChange={(e) => setClosed(e.target.checked)}
            />
            <span>
              <span className="font-medium">Stop-sell these dates</span>
              <span className="block text-[12px] text-muted-foreground">
                The hotel has closed the dates to us entirely. Different from zero rooms: Sales
                will not be prompted to ask you for more.
              </span>
            </span>
          </label>
        </div>

        <div>
          <label className="mb-1 block text-[13px] font-medium">
            Apply to selected days only <span className="font-normal text-muted-foreground">(optional)</span>
          </label>
          <div className="flex flex-wrap gap-1.5">
            {WEEKDAYS.map((day) => {
              const active = weekdays.includes(day.value);
              return (
                <button
                  key={day.value}
                  type="button"
                  onClick={() => toggleWeekday(day.value)}
                  className={`rounded-md border px-2.5 py-1 text-[12px] transition ${
                    active
                      ? "border-slate-900 bg-slate-900 text-white"
                      : "border-slate-200 bg-white text-slate-600 hover:border-slate-400"
                  }`}
                >
                  {day.label}
                </button>
              );
            })}
          </div>
          <p className="mt-1 text-[12px] text-muted-foreground">
            Leave empty for every day. Useful when weekend quota differs from midweek.
          </p>
        </div>

        <div>
          <label className="mb-1 block text-[13px] font-medium">
            Note <span className="font-normal text-muted-foreground">(optional)</span>
          </label>
          <Input
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="e.g. Group block for ABC Corp"
          />
        </div>

        {error && (
          <p className="rounded-md bg-red-50 px-3 py-2 text-[13px] text-red-700">{error}</p>
        )}
      </DialogBody>

      <DialogFooter>
        <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={publish.isPending}>
          Cancel
        </Button>
        <Button onClick={handleSubmit} disabled={!canSubmit || publish.isPending}>
          {publish.isPending ? "Saving..." : "Publish"}
        </Button>
      </DialogFooter>
    </DialogContent>
  );
}
