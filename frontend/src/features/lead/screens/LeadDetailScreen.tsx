import { ModulePlaceholder } from "@/shared/components/ModulePlaceholder";

type LeadDetailScreenProps = {
  leadId: string;
};

export function LeadDetailScreen({ leadId }: LeadDetailScreenProps) {
  return (
    <ModulePlaceholder
      title="Lead Detail"
      description="Inspect a lead, related interactions, tasks, quotations, and deal progress."
      cardTitle="Lead record"
      cardDescription={`Lead ${leadId} is connected through the dynamic route and ready for detail data.`}
    />
  );
}
