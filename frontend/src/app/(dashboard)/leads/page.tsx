import { Suspense } from "react";
import { LeadListScreen } from "@/features/lead/screens/LeadListScreen";

/**
 * The screen reads `?lead=<id>` (see `LeadListScreen`) to open a lead's drawer,
 * and `useSearchParams` forces its subtree out of static prerendering. The
 * Suspense boundary is what keeps that from failing the build — without it Next
 * refuses to prerender the route at all.
 */
export default function LeadsPage() {
  return (
    <Suspense>
      <LeadListScreen />
    </Suspense>
  );
}
