type AuthLayoutProps = {
  children: React.ReactNode;
};

export function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-zinc-50 px-4 py-10">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <p className="text-sm font-medium uppercase tracking-wide text-teal-700">
            Leadora
          </p>
          <h1 className="mt-2 text-3xl font-semibold text-zinc-950">
            Hotel sales workflow
          </h1>
        </div>
        {children}
      </div>
    </main>
  );
}
