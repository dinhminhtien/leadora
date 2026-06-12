import { LeadDetailScreen } from "@/features/lead/screens/LeadDetailScreen";

type LeadDetailPageProps = {
  params: Promise<{
    id: string;
  }>;
};

export default async function LeadDetailPage({ params }: LeadDetailPageProps) {
  const { id } = await params;

  return <LeadDetailScreen leadId={id} />;
}
