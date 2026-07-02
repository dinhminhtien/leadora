"use client";

import React from "react";

/**
 * "Lia" — the Leadora care & follow-up assistant mascot, drawn inline as an SVG so it scales
 * crisply at any size (launcher button, panel header, empty state) and needs no asset hosting.
 *
 * Palette is fixed to Lia's brand teal + pink regardless of the app light/dark theme, so she
 * always looks like herself.
 */
export type LiaMascotProps = {
  size?: number;
  /** "full" shows head + body; "head" shows just the face — used in the small round launcher. */
  variant?: "full" | "head";
  /** Gentle idle animation (antenna sway + blink). */
  animated?: boolean;
  className?: string;
};

const TEAL = "#3FC8B4";
const TEAL_DARK = "#1F9E8C";
const FACE = "#EAFBF6";
const PINK = "#FF8FB8";
const PINK_SOFT = "#FFB9CE";
const INK = "#173A35";
const CHEST = "#143731";

export function LiaMascot({
  size = 64,
  variant = "full",
  animated = true,
  className,
}: LiaMascotProps) {
  const head = variant === "head";
  // The head-only crop zooms into the face so it fills a round launcher nicely.
  const viewBox = head ? "44 6 112 118" : "8 4 184 212";
  const w = head ? size : size * (184 / 212);

  return (
    <svg
      width={w}
      height={size}
      viewBox={viewBox}
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      role="img"
      aria-label="Lia assistant"
    >
      {/* ── Body (hidden in head-only crop) ───────────────────────────── */}
      {!head && (
        <g>
          {/* raised waving arm */}
          <path
            d="M133 150 q34 -10 30 -44"
            stroke={TEAL_DARK}
            strokeWidth="15"
            strokeLinecap="round"
            fill="none"
          />
          {/* torso */}
          <rect x="64" y="120" width="72" height="70" rx="22" fill={TEAL} stroke={TEAL_DARK} strokeWidth="5" />
          {/* chest panel + heart */}
          <rect x="82" y="134" width="36" height="36" rx="10" fill={CHEST} />
          <path
            d="M100 162 c-7 -6 -13 -10 -13 -16 a6.5 6.5 0 0 1 13 -2 a6.5 6.5 0 0 1 13 2 c0 6 -6 10 -13 16 z"
            fill={PINK}
          />
          {/* legs */}
          <ellipse cx="84" cy="196" rx="11" ry="8" fill={TEAL_DARK} />
          <ellipse cx="116" cy="196" rx="11" ry="8" fill={TEAL_DARK} />
        </g>
      )}

      {/* ── Headphones ────────────────────────────────────────────────── */}
      <path d="M58 86 q-14 0 -14 -18" stroke={PINK} strokeWidth="11" strokeLinecap="round" fill="none" />
      <circle cx="56" cy="92" r="11" fill={PINK} />
      <path d="M142 86 q14 0 14 -18" stroke={PINK} strokeWidth="11" strokeLinecap="round" fill="none" />
      <circle cx="144" cy="92" r="11" fill={PINK} />

      {/* ── Antenna ───────────────────────────────────────────────────── */}
      <g className={animated ? "lia-antenna" : undefined} style={{ transformOrigin: "100px 44px" }}>
        <path d="M100 44 q-3 -16 6 -26" stroke={TEAL_DARK} strokeWidth="6" strokeLinecap="round" fill="none" />
        <circle cx="108" cy="16" r="7" fill={PINK} />
      </g>

      {/* ── Head ──────────────────────────────────────────────────────── */}
      <rect x="55" y="42" width="90" height="80" rx="26" fill={TEAL} stroke={TEAL_DARK} strokeWidth="5" />
      <rect x="67" y="54" width="66" height="56" rx="18" fill={FACE} />

      {/* cheeks */}
      <ellipse cx="80" cy="92" rx="7" ry="4.5" fill={PINK_SOFT} />
      <ellipse cx="120" cy="92" rx="7" ry="4.5" fill={PINK_SOFT} />

      {/* eyes (blink via scaleY) */}
      <g className={animated ? "lia-eyes" : undefined}>
        <circle cx="88" cy="80" r="7.5" fill={INK} />
        <circle cx="112" cy="80" r="7.5" fill={INK} />
        <circle cx="90.5" cy="77.5" r="2.2" fill="#fff" />
        <circle cx="114.5" cy="77.5" r="2.2" fill="#fff" />
      </g>

      {/* smile */}
      <path d="M89 92 q11 10 22 0" stroke={INK} strokeWidth="4" strokeLinecap="round" fill="none" />

      <style>{`
        @keyframes lia-sway { 0%,100% { transform: rotate(-6deg);} 50% { transform: rotate(8deg);} }
        @keyframes lia-blink { 0%,92%,100% { transform: scaleY(1);} 96% { transform: scaleY(0.1);} }
        .lia-antenna { animation: lia-sway 3.2s ease-in-out infinite; }
        .lia-eyes { animation: lia-blink 4.5s ease-in-out infinite; transform-origin: 100px 80px; }
        @media (prefers-reduced-motion: reduce) {
          .lia-antenna, .lia-eyes { animation: none; }
        }
      `}</style>
    </svg>
  );
}
