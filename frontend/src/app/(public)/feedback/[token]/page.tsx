import { SubmitFeedbackScreen } from "@/features/customer_feedback/screens/SubmitFeedbackScreen";

type FeedbackPageProps = {
  params: Promise<{
    token: string;
  }>;
};

export default async function FeedbackPage({ params }: FeedbackPageProps) {
  const { token } = await params;

  return <SubmitFeedbackScreen token={token} />;
}
