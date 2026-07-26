import { Suspense } from "react";

import { RoomRequestInboxScreen } from "@/features/room_request/screens/RoomRequestInboxScreen";
import { LoadingState } from "@/shared/components/LoadingState";

export default function RoomRequestsPage() {
  return (
    <Suspense fallback={<LoadingState label="Loading room requests..." />}>
      <RoomRequestInboxScreen />
    </Suspense>
  );
}
