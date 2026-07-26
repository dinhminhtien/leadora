import React, { useState, useEffect, useRef } from "react";

export interface UserSummary {
  userId: string;
  fullName: string;
  email: string;
  roleName: string | null;
}

interface UserSelectProps {
  users: UserSummary[];
  value: string;
  onChange: (email: string, fullName: string) => void;
  disabled?: boolean;
}

export const UserSelect: React.FC<UserSelectProps> = ({ users, value, onChange, disabled }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [search, setSearch] = useState("");
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const selectedUser = users.find(u => u.email === value);

  const filteredUsers = users.filter(u =>
    u.fullName.toLowerCase().includes(search.toLowerCase()) ||
    u.email.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="relative w-full" ref={dropdownRef}>
      <button
        type="button"
        disabled={disabled}
        onClick={() => setIsOpen(!isOpen)}
        className="w-full min-h-10.5 px-3 py-1.5 flex items-center justify-between text-left rounded-xl border border-border bg-input text-sm text-foreground focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/10 shadow-[inset_0_1.5px_3px_rgba(0,0,0,0.025)] disabled:cursor-not-allowed disabled:opacity-50 dark:shadow-none transition"
      >
        {selectedUser ? (
          <div className="flex flex-col">
            <span className="font-semibold text-xs text-slate-800 dark:text-slate-200 leading-tight">
              {selectedUser.fullName}
            </span>
            <span className="text-[10px] text-slate-500 mt-0.5 leading-none">
              {selectedUser.email} ({selectedUser.roleName || "Staff"})
            </span>
          </div>
        ) : (
          <span className="text-slate-400 text-xs">Select Deal Owner...</span>
        )}
        <svg
          className={`h-4 w-4 text-slate-400 transition-transform duration-200 ${isOpen ? "rotate-180" : ""}`}
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {isOpen && (
        <div className="absolute top-full left-0 z-100 mt-1.5 w-full rounded-xl border border-slate-200/60 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-xl p-2 flex flex-col gap-2 animate-in fade-in slide-in-from-top-1 duration-100">
          <input
            type="text"
            placeholder="Search owner by name or email..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="w-full px-3 py-1.5 text-xs border border-slate-100 dark:border-slate-800 rounded-lg focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary/10 bg-slate-50 dark:bg-slate-950 text-foreground"
            onClick={e => e.stopPropagation()}
            autoFocus
          />
          <div className="overflow-y-auto max-h-45 flex flex-col gap-1 pr-1">
            {filteredUsers.length > 0 ? (
              filteredUsers.map(u => (
                <button
                  key={u.userId}
                  type="button"
                  onClick={() => {
                    onChange(u.email, u.fullName);
                    setIsOpen(false);
                    setSearch("");
                  }}
                  className={`w-full text-left px-3 py-2 rounded-lg flex items-center justify-between transition hover:bg-slate-50 dark:hover:bg-slate-800/50 ${u.email === value ? "bg-primary/5 border-l-2 border-primary pl-2.5" : ""
                    }`}
                >
                  <div className="flex flex-col">
                    <span className="font-semibold text-xs text-slate-800 dark:text-slate-200">
                      {u.fullName}
                    </span>
                    <span className="text-[10px] text-slate-400 mt-0.5">
                      {u.email}
                    </span>
                  </div>
                  <span className="text-[9px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400">
                    {u.roleName || "Staff"}
                  </span>
                </button>
              ))
            ) : (
              <span className="text-center text-xs text-slate-400 py-3">No owners found</span>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
