import React from "react";

type TableProps = React.TableHTMLAttributes<HTMLTableElement>;

export const Table = React.forwardRef<HTMLTableElement, TableProps>(
  ({ className, ...props }, ref) => (
    <div className="w-full overflow-x-auto rounded-lg border border-border">
      <table
        ref={ref}
        className={`w-full border-collapse text-sm ${className || ""}`}
        {...props}
      />
    </div>
  )
);

Table.displayName = "Table";

type TableHeaderProps = React.HTMLAttributes<HTMLTableSectionElement>;

export const TableHeader = React.forwardRef<
  HTMLTableSectionElement,
  TableHeaderProps
>(({ className, ...props }, ref) => (
  <thead
    ref={ref}
    className={`bg-muted border-b border-border ${className || ""}`}
    {...props}
  />
));

TableHeader.displayName = "TableHeader";

type TableBodyProps = React.HTMLAttributes<HTMLTableSectionElement>;

export const TableBody = React.forwardRef<
  HTMLTableSectionElement,
  TableBodyProps
>(({ className, ...props }, ref) => (
  <tbody ref={ref} className={`${className || ""}`} {...props} />
));

TableBody.displayName = "TableBody";

type TableHeadProps = React.ThHTMLAttributes<HTMLTableCellElement>;

export const TableHead = React.forwardRef<HTMLTableCellElement, TableHeadProps>(
  ({ className, ...props }, ref) => (
    <th
      ref={ref}
      className={`px-6 py-3 text-left text-sm font-semibold text-foreground ${
        className || ""
      }`}
      {...props}
    />
  )
);

TableHead.displayName = "TableHead";

type TableCellProps = React.TdHTMLAttributes<HTMLTableCellElement>;

export const TableCell = React.forwardRef<HTMLTableCellElement, TableCellProps>(
  ({ className, ...props }, ref) => (
    <td
      ref={ref}
      className={`px-6 py-4 text-foreground border-b border-border ${
        className || ""
      }`}
      {...props}
    />
  )
);

TableCell.displayName = "TableCell";

interface TableRowProps extends React.HTMLAttributes<HTMLTableRowElement> {
  hoverable?: boolean;
}

export const TableRow = React.forwardRef<HTMLTableRowElement, TableRowProps>(
  ({ className, hoverable = true, ...props }, ref) => (
    <tr
      ref={ref}
      className={`${hoverable ? "hover:bg-muted transition-colors" : ""} ${
        className || ""
      }`}
      {...props}
    />
  )
);

TableRow.displayName = "TableRow";
