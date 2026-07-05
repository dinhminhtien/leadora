import { Suspense } from "react";
import { BookingConfirmationScreen } from "@/features/booking_confirmation/screens/BookingConfirmationScreen";
import { LoadingState } from "@/shared/components/LoadingState";

export default function BookingConfirmationPage() {
  return (
    <Suspense fallback={<LoadingState label="Loading bookings..." />}>
      <BookingConfirmationScreen />
    </Suspense>
  );
}
