"use client";

import React, { createContext, useContext, useState } from "react";

type TabsContextValue = {
  value: string;
  onValueChange: (v: string) => void;
};

const TabsContext = createContext<TabsContextValue>({ value: "", onValueChange: () => {} });

export function Tabs({
  value: controlledValue,
  defaultValue,
  onValueChange,
  children,
  className = "",
}: {
  value?: string;
  defaultValue?: string;
  onValueChange?: (v: string) => void;
  children: React.ReactNode;
  className?: string;
}) {
  const [internal, setInternal] = useState(defaultValue ?? "");
  const value = controlledValue ?? internal;
  const handleChange = (v: string) => {
    if (controlledValue === undefined) setInternal(v);
    onValueChange?.(v);
  };
  return (
    <TabsContext.Provider value={{ value, onValueChange: handleChange }}>
      <div className={className}>{children}</div>
    </TabsContext.Provider>
  );
}

export function TabsList({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div role="tablist" className={`flex gap-1 ${className}`}>
      {children}
    </div>
  );
}

export function TabsTrigger({
  value,
  children,
  className = "",
}: {
  value: string;
  children: React.ReactNode;
  className?: string;
}) {
  const ctx = useContext(TabsContext);
  const active = ctx.value === value;
  return (
    <button
      role="tab"
      aria-selected={active}
      onClick={() => ctx.onValueChange(value)}
      className={`px-4 py-2.5 text-sm font-medium transition-colors border-b-2 ${
        active
          ? "border-primary text-primary"
          : "border-transparent text-muted-foreground hover:text-foreground hover:border-border"
      } ${className}`}
    >
      {children}
    </button>
  );
}

export function TabsContent({
  value,
  children,
  className = "",
}: {
  value: string;
  children: React.ReactNode;
  className?: string;
}) {
  const ctx = useContext(TabsContext);
  if (ctx.value !== value) return null;
  return <div className={className}>{children}</div>;
}
