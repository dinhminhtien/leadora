"use client";

import { use } from "react";
import { CustomerProfileDetailScreen } from "@/features/customer_profile/screens/CustomerProfileDetailScreen";

interface Props {
  params: Promise<{ id: string }>;
}

export default function CustomerProfileDetailPage({ params }: Props) {
  const { id } = use(params);
  return <CustomerProfileDetailScreen customerId={id} />;
}
