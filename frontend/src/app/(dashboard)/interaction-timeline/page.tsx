import { Suspense } from "react";
import { InteractionTimelineScreen } from "@/features/interaction_timeline/screens/InteractionTimelineScreen";
import { ListSkeleton } from "@/components/ui/skeletons";

export default function InteractionTimelinePage() {
  return (
    <Suspense fallback={<ListSkeleton count={8} />}>
      <InteractionTimelineScreen />
    </Suspense>
  );
}
