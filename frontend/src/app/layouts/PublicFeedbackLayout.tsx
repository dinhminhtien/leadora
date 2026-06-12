type PublicFeedbackLayoutProps = {
  children: React.ReactNode;
};

export function PublicFeedbackLayout({ children }: PublicFeedbackLayoutProps) {
  return (
    <main className="min-h-screen bg-zinc-50 px-4 py-10">
      <div className="mx-auto max-w-3xl">
        <div className="mb-8">
          <p className="text-sm font-semibold uppercase tracking-wide text-teal-700">
            Leadora
          </p>
          <h1 className="mt-2 text-3xl font-semibold text-zinc-950">
            Guest feedback
          </h1>
        </div>
        {children}
      </div>
    </main>
  );
}
