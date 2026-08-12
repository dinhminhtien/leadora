import { Suspense } from "react";

import { RoomAvailabilityScreen } from "@/features/room_availability/screens/RoomAvailabilityScreen";
import { LoadingState } from "@/shared/components/LoadingState";

export default function RoomAvailabilityPage() {
  return (
    <Suspense fallback={<LoadingState label="Loading room allotment..." />}>
      <RoomAvailabilityScreen />
    </Suspense>
  );
}
