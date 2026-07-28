"use client";

/**
 * Customer detail drawer — built on the shared `RecordDetailDrawer` (§2.11).
 *
 * Supplies data only; the header, sections, empty-value handling and footer all
 * come from the shared component, so this drawer is identical in behaviour to
 * the booking, payment, reminder and handover ones.
 *
 * **BR-09 is surfaced, not enforced here.** A corporate customer must name its
 * company; the server rejects a save without one. The drawer flags the gap so a
 * rep sees it before opening the edit form, rather than after a refused save.
 */

import * as React from "react";
import {
  Building2,
  CalendarDays,
  Hash,
  Mail,
  MapPin,
  Pencil,
  Phone,
  User,
} from "lucide-react";

import {
  RecordDetailDrawer,
  formatDetailDate,
} from "@/components/ui/record-drawer";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import type { Customer } from "@/services/customer_profile_service";

export function CustomerDetailDrawer({
  customer,
  onOpenChange,
  onEdit,
}: {
  customer: Customer | null;
  onOpenChange: (open: boolean) => void;
  onEdit?: (customer: Customer) => void;
}) {
  if (!customer) {
    return <RecordDetailDrawer open={false} onOpenChange={onOpenChange} title="" sections={[]} />;
  }

  const isCorporate = customer.customerType === "CORPORATE";
  const missingCompany = isCorporate && !customer.companyName?.trim();

  return (
    <RecordDetailDrawer
      open
      onOpenChange={onOpenChange}
      avatarName={customer.fullName}
      title={customer.fullName}
      subtitle={{
        icon: isCorporate ? Building2 : User,
        text: isCorporate ? (customer.companyName || "Organization") : "Individual",
      }}
      status={{ domain: "customer", value: customer.status }}
      recordId={customer.customerId}
      fullPageHref={`${ROUTE_PATHS.customerProfiles}/${customer.customerId}`}
      actions={
        onEdit
          ? [
              {
                label: "Edit",
                icon: Pencil,
                onClick: () => onEdit(customer),
              },
            ]
          : []
      }
      notice={
        missingCompany
          ? {
              tone: "warning",
              text: "This corporate customer has no company name. Saving any edit will be refused until one is added.",
            }
          : undefined
      }
      sections={[
        {
          title: "Contact",
          rows: [
            {
              label: "Email",
              icon: Mail,
              value: customer.email ? (
                <a
                  href={`mailto:${customer.email}`}
                  className="truncate text-brand-600 hover:underline dark:text-brand-500"
                >
                  {customer.email}
                </a>
              ) : null,
            },
            {
              label: "Phone",
              icon: Phone,
              value: customer.phone ? (
                <a
                  href={`tel:${customer.phone}`}
                  className="text-brand-600 hover:underline dark:text-brand-500"
                >
                  {customer.phone}
                </a>
              ) : null,
            },
            { label: "Address", icon: MapPin, value: customer.address },
          ],
        },
        {
          title: isCorporate ? "Organization" : "Profile",
          rows: [
            {
              label: "Type",
              value: isCorporate ? "Corporate" : "Individual",
            },
            ...(isCorporate
              ? [
                  { label: "Company", icon: Building2, value: customer.companyName },
                  { label: "Tax code", icon: Hash, value: customer.taxCode },
                ]
              : []),
          ],
        },
        {
          title: "Ownership",
          rows: [
            { label: "Assigned to", icon: User, value: customer.assignedUserName },
            { label: "Created by", value: customer.createdByName },
            {
              label: "Customer since",
              icon: CalendarDays,
              value: formatDetailDate(customer.createdAt),
            },
            { label: "Last updated", value: formatDetailDate(customer.updatedAt) },
          ],
        },
      ]}
    />
  );
}
