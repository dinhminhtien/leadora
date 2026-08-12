"use client";

/**
 * Bulk import of the hotel's allotment spreadsheet.
 *
 * <p>Publishing a range at a time is fine until the quota varies by day and room type, which is
 * the normal case. The hotel already produces the numbers in a file, so the file goes in
 * directly rather than being retyped a row at a time.
 *
 * <p>Rejected rows are shown, not summarised away. A row that failed to import is a night that
 * will read as "not published" and quietly send quotations to the Reservation desk — the desk
 * has to be able to see which lines to fix, with their line numbers, rather than re-uploading
 * the file and hoping.
 */

import * as React from "react";
import { Upload } from "lucide-react";

import { Button } from "@/components/ui/Button";
import { apiErrorMessage } from "@/services/api_error";
import type { AllotmentImportRejection } from "@/services/room_availability_service";
import { toast } from "@/stores/toast_store";
import { useImportAllotment } from "@/features/room_availability/hooks/use_room_availability";

export function ImportAllotmentButton() {
  const inputRef = React.useRef<HTMLInputElement>(null);
  const importAllotment = useImportAllotment();
  const [rejected, setRejected] = React.useState<AllotmentImportRejection[]>([]);

  const handleFile = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    // Reset immediately so choosing the same file twice still fires a change event — otherwise
    // a corrected re-upload of the same filename silently does nothing.
    event.target.value = "";
    if (!file) return;

    setRejected([]);
    try {
      const response = await importAllotment.mutateAsync(file);
      const result = response.data;
      setRejected(result.rejected ?? []);

      if (result.rowsRejected > 0) {
        toast.warning(
          `${result.nightsImported} night(s) imported, ${result.rowsRejected} row(s) rejected`,
        );
      } else {
        toast.success(`${result.nightsImported} night(s) imported`);
      }
    } catch (e) {
      toast.error(apiErrorMessage(e));
    }
  };

  return (
    <>
      <input
        ref={inputRef}
        type="file"
        accept=".csv,text/csv"
        className="hidden"
        onChange={handleFile}
      />
      <Button
        variant="ghost"
        onClick={() => inputRef.current?.click()}
        disabled={importAllotment.isPending}
      >
        <Upload className="mr-1.5 h-4 w-4" />
        {importAllotment.isPending ? "Importing..." : "Import CSV"}
      </Button>

      {rejected.length > 0 && (
        <div className="mt-2 w-full rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5 text-[12px] text-amber-900">
          <p className="mb-1 font-medium">
            {rejected.length} row(s) were not imported — fix these lines and re-upload:
          </p>
          <ul className="space-y-0.5">
            {rejected.slice(0, 20).map((row) => (
              <li key={row.line}>
                <span className="font-mono">line {row.line}</span> — {row.reason}
              </li>
            ))}
          </ul>
          {rejected.length > 20 && (
            <p className="mt-1">...and {rejected.length - 20} more.</p>
          )}
          <p className="mt-1.5 text-amber-800">
            Expected columns: <span className="font-mono">room_type, date, allotted</span> (plus
            optional <span className="font-mono">closed</span>). Dates as YYYY-MM-DD.
          </p>
        </div>
      )}
    </>
  );
}
