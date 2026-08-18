"use client";

/**
 * CSV export and print helpers shared by the UC-23 report tabs.
 *
 * Extracted from the copy that lived inline in the discount-report tab, with its escaping bug
 * fixed: that version joined cells with a bare `,`, so a customer named `Công ty A, B & C` split
 * itself across two columns and shifted every field after it.
 */

/** A block of label/value pairs — the KPI tiles at the top of a report. */
export type CsvSection = { title: string; rows: [string, string | number][] };

/** A tabular block — the per-rep / per-stage detail tables. */
export type CsvTable = { title: string; headers: string[]; rows: (string | number)[][] };

/**
 * RFC 4180 escaping: wrap in quotes when the value contains a comma, quote, or newline, and double
 * any embedded quote. Leaving this out is not a cosmetic issue — it silently corrupts every row
 * containing a comma, which in this product means most company names.
 */
function escapeCell(value: string | number | null | undefined): string {
  const text = value === null || value === undefined ? "" : String(value);
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

function toCsvRow(cells: (string | number)[]): string {
  return cells.map(escapeCell).join(",");
}

/**
 * Builds a CSV out of an optional summary block plus any number of tables, and downloads it.
 *
 * The leading BOM is what makes Excel on Windows read the file as UTF-8; without it Vietnamese
 * diacritics arrive as mojibake, which is the single most common complaint about exported reports.
 */
export function downloadReportCsv(options: {
  filename: string;
  /** Rendered as a header block above the tables — period, scope, generated-at. */
  meta?: [string, string | number][];
  sections?: CsvSection[];
  tables?: CsvTable[];
}): void {
  const lines: string[] = [];

  if (options.meta?.length) {
    for (const [label, value] of options.meta) {
      lines.push(toCsvRow([label, value]));
    }
    lines.push("");
  }

  for (const section of options.sections ?? []) {
    lines.push(toCsvRow([section.title]));
    lines.push(toCsvRow(["Metric", "Value"]));
    for (const [label, value] of section.rows) {
      lines.push(toCsvRow([label, value]));
    }
    lines.push("");
  }

  for (const table of options.tables ?? []) {
    lines.push(toCsvRow([table.title]));
    lines.push(toCsvRow(table.headers));
    for (const row of table.rows) {
      lines.push(toCsvRow(row));
    }
    lines.push("");
  }

  const csv = "﻿" + lines.join("\r\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = options.filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

/** Filename stem carrying the period, so downloads do not collide in the browser's folder. */
export function reportFilename(slug: string, dateFrom?: string, dateTo?: string): string {
  const period = dateFrom || dateTo ? `${dateFrom || "start"}_${dateTo || "today"}` : "all-time";
  return `${slug}-${period}.csv`;
}

/** Human label for the selected period, reused in the CSV header and on screen. */
export function periodLabel(dateFrom?: string, dateTo?: string): string {
  if (!dateFrom && !dateTo) return "All time";
  if (dateFrom && dateTo) return `${dateFrom} to ${dateTo}`;
  return dateFrom ? `From ${dateFrom}` : `Up to ${dateTo}`;
}

export function printReport(): void {
  window.print();
}
