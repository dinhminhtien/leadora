import { ReviseQuotationScreen } from "@/features/quotation/screens/ReviseQuotationScreen";

type Props = { params: Promise<{ id: string }> };

export default async function ReviseQuotationPage({ params }: Props) {
  const { id } = await params;
  return <ReviseQuotationScreen quotationId={id} />;
}
