import { ModulePlaceholder } from "@/shared/components/ModulePlaceholder";

type SubmitFeedbackScreenProps = {
  token: string;
};

export function SubmitFeedbackScreen({ token }: SubmitFeedbackScreenProps) {
  return (
    <ModulePlaceholder
      title="Submit Feedback"
      description="Collect guest feedback through a public tokenized link."
      cardTitle="Public feedback form"
      cardDescription={`Feedback token ${token} is ready to be submitted to the customer feedback service.`}
    />
  );
}
