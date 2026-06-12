import { PageHeader } from "@/shared/components/PageHeader";

type ModulePlaceholderProps = {
  title: string;
  description: string;
  cardTitle?: string;
  cardDescription?: string;
};

export function ModulePlaceholder({
  title,
  description,
  cardTitle = "Foundation ready",
  cardDescription = "This module is wired into the application structure and ready for feature-specific workflows.",
}: ModulePlaceholderProps) {
  return (
    <div>
      <PageHeader title={title} description={description} />
      <section className="rounded-lg border border-zinc-200 bg-white p-6 shadow-sm">
        <h2 className="text-base font-semibold text-zinc-950">{cardTitle}</h2>
        <p className="mt-2 text-sm leading-6 text-zinc-600">
          {cardDescription}
        </p>
      </section>
    </div>
  );
}
