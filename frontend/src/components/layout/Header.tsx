"use client";

import React, { useState } from "react";
import {
  Bell,
  LogOut,
  Settings,
  User,
  Search,
  Menu,
  X,
} from "lucide-react";
import { Button } from "@/components/ui/Button";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/stores/auth_store";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { authService } from "@/services/auth_service";
import { supabaseAuthService } from "@/services/supabase_auth_service";

export const Header: React.FC = () => {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);
  const router = useRouter();
  const { clearUser } = useAuthStore();

  const handleLogout = async () => {
    localStorage.removeItem("accessToken");
    clearUser();
    
    try {
      await Promise.allSettled([
        authService.logout(),
        supabaseAuthService.signOut(),
      ]);
    } catch (e) {
      console.warn("Failed to complete services logout", e);
    }

    router.push(ROUTE_PATHS.login || "/login");
  };

  return (
    <header className="sticky top-0 z-20 border-b border-border bg-background">
      <div className="flex h-16 items-center justify-between px-4 sm:px-6 lg:px-8">
        {/* Left: Logo */}
        <div className="flex items-center gap-3">
          <div className="hidden lg:flex items-center gap-3">
            <div className="flex size-9 items-center justify-center rounded-lg bg-primary text-primary-foreground font-semibold">
              L
            </div>
            <div>
              <p className="text-sm font-semibold text-foreground">Leadora</p>
              <p className="text-xs text-muted-foreground">CRM</p>
            </div>
          </div>
        </div>

        {/* Center: Search */}
        <div className="hidden flex-1 max-w-md md:flex">
          <div className="relative w-full">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <input
              type="text"
              placeholder="Search leads, deals, customers..."
              className="w-full rounded-lg border border-border bg-input pl-10 pr-4 py-2 text-sm text-foreground placeholder-muted-foreground focus-visible:outline-none focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/20"
            />
          </div>
        </div>

        {/* Right: Actions */}
        <div className="flex items-center gap-2">
          {/* Notifications */}
          <button
            className="relative rounded-lg p-2 hover:bg-muted transition-colors"
            title="Notifications"
          >
            <Bell className="size-5 text-muted-foreground" />
            <span className="absolute right-1 top-1 size-2 rounded-full bg-danger"></span>
          </button>

          {/* User Menu */}
          <div className="relative">
            <button
              onClick={() => setIsUserMenuOpen(!isUserMenuOpen)}
              className="flex items-center gap-2 rounded-lg p-2 hover:bg-muted transition-colors"
            >
              <div className="flex size-8 items-center justify-center rounded-full bg-primary text-primary-foreground text-xs font-semibold">
                JD
              </div>
              <div className="hidden sm:flex flex-col items-start">
                <span className="text-sm font-medium text-foreground">
                  John Doe
                </span>
                <span className="text-xs text-muted-foreground">
                  Sales Manager
                </span>
              </div>
            </button>

            {/* User Menu Dropdown */}
            {isUserMenuOpen && (
              <div className="absolute right-0 mt-2 w-48 rounded-lg border border-border bg-background shadow-lg">
                <button className="flex w-full items-center gap-3 px-4 py-2 text-sm text-foreground hover:bg-muted transition-colors">
                  <User className="size-4" />
                  Profile
                </button>
                <button className="flex w-full items-center gap-3 px-4 py-2 text-sm text-foreground hover:bg-muted transition-colors">
                  <Settings className="size-4" />
                  Settings
                </button>
                <button 
                  onClick={handleLogout}
                  className="flex w-full items-center gap-3 px-4 py-2 text-sm text-danger hover:bg-muted transition-colors border-t border-border"
                >
                  <LogOut className="size-4" />
                  Logout
                </button>
              </div>
            )}
          </div>

          {/* Mobile Menu Toggle */}
          <button
            onClick={() => setIsMenuOpen(!isMenuOpen)}
            className="rounded-lg p-2 hover:bg-muted transition-colors lg:hidden"
          >
            {isMenuOpen ? (
              <X className="size-5" />
            ) : (
              <Menu className="size-5" />
            )}
          </button>
        </div>
      </div>
    </header>
  );
};

Header.displayName = "Header";
