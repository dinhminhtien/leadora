"use client";

/**
 * Customer detail drawer — built on the shared `RecordDetailDrawer` (§2.11).
 *
 * **Popup parity (§9.3).** The body is the *actual* `CustomerProfileDetailScreen`
 * in embedded mode, not a reduced summary of it. The drawer previously showed
 * three flat sections while the full page had Overview · Tasks · History · Info,
 * so a rep who peeked at a customer saw none of their tasks or service history
 * and had to navigate away to do anything useful. Mounting the real screen makes
 * the two impossible to drift apart: every tab, count and action the page gains
 * appears here the same day, because it is the same component.
 *
 * The drawer still owns the identity chrome — avatar, title, status pill, record
 * id and the "open in full page" link — and the embedded screen suppresses its
 * own header and profile card to avoid rendering that twice.
 *
 * **BR-09 is surfaced, not enforced here.** A corporate customer must name its
 * company; the server rejects a save without one. The drawer flags the gap so a
 * rep sees it before opening the edit form, rather than after a refused save.
 */

import * as React from "react";
import { Building2, Pencil, User } from "lucide-react";

import { RecordDetailDrawer } from "@/components/ui/record-drawer";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { CustomerProfileDetailScreen } from "@/features/customer_profile/screens/CustomerProfileDetailScreen";
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
    return <RecordDetailDrawer open={false} onOpenChange={onOpenChange} title="" />;
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
    >
      {/* The real detail screen — same tabs, same counts, same actions. */}
      <CustomerProfileDetailScreen customerId={customer.customerId} embedded />
    </RecordDetailDrawer>
  );
}
