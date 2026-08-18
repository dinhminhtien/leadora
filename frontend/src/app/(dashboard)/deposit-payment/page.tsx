import { Suspense } from "react";
import { DepositPaymentScreen } from "@/features/deposit_payment/screens/DepositPaymentScreen";

export default function DepositPaymentPage() {
  return (
    <Suspense fallback={<div className="p-8 text-center text-gray-500">Loading payment system...</div>}>
      <DepositPaymentScreen />
    </Suspense>
  );
}
