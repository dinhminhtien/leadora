import { LeadDetailScreen } from "@/features/lead/screens/LeadDetailScreen";

type LeadDetailPageProps = {
  params: Promise<{
    id: string;
  }>;
  searchParams: Promise<{ mode?: string }>;
};

export default async function LeadDetailPage({ params, searchParams }: LeadDetailPageProps) {
  const { id } = await params;
  const { mode } = await searchParams;

  // ?mode=edit (set by the "Created by me" list rows) → restricted edit-only screen.
  return <LeadDetailScreen leadId={id} editMode={mode === "edit"} />;
}
