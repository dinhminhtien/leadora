type EmptyStateProps = {
  title: string;
  description?: string;
};

export function EmptyState({ title, description }: EmptyStateProps) {
  return (
    <div className="rounded-lg border border-dashed border-zinc-300 bg-white p-8 text-center">
      <h2 className="text-base font-semibold text-zinc-950">{title}</h2>
      {description ? (
        <p className="mt-2 text-sm leading-6 text-zinc-600">{description}</p>
      ) : null}
    </div>
  );
}
