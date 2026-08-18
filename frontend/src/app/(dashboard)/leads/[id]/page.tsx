import { redirect } from "next/navigation";

type LeadDetailPageProps = {
  params: Promise<{ id: string }>;
};

/**
 * `/leads/{id}` no longer renders anything of its own.
 *
 * A lead used to have two detail surfaces — this full page and the drawer over
 * the list — showing the same record in two different layouts that drifted
 * apart. The page is gone; the URL is not, because notification e-mails, SLA
 * escalations and bookmarks already point at it. It redirects to the list with
 * the lead pre-opened, so an old link still lands on the right record.
 *
 * A server-side redirect rather than a client one: it costs no JavaScript and
 * the user never sees an empty page flash before it moves.
 */
export default async function LeadDetailPage({ params }: LeadDetailPageProps) {
  const { id } = await params;
  redirect(`/leads?lead=${encodeURIComponent(id)}`);
}
