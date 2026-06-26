import type { NextConfig } from "next";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

// The whole project shares ONE .env at the repo root (leadora/.env), one level
// above this frontend dir. Next.js only auto-loads env files inside the project
// directory, so we load the shared file here and inject its values into
// process.env before the build. NEXT_PUBLIC_* vars are then inlined as usual.
// Real environment variables and a local .env/.env.local still take precedence
// (we only set keys that are not already defined).
function loadSharedEnv(): void {
  try {
    const envPath = resolve(process.cwd(), "../.env");
    const content = readFileSync(envPath, "utf8");
    for (const rawLine of content.split(/\r?\n/)) {
      const line = rawLine.trim();
      if (!line || line.startsWith("#")) continue;
      const eq = line.indexOf("=");
      if (eq === -1) continue;
      const key = line.slice(0, eq).trim();
      let value = line.slice(eq + 1).trim();
      if (
        (value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))
      ) {
        value = value.slice(1, -1);
      }
      if (key && process.env[key] === undefined) {
        process.env[key] = value;
      }
    }
  } catch {
    // Shared root .env not found — fall back to Next.js default env resolution.
  }
}

loadSharedEnv();

// Same-origin API proxy. The browser calls relative "/api/v1/*"
// (NEXT_PUBLIC_API_BASE_URL=/api/v1 in .env.local, needed for ngrok/same-origin),
// and Next forwards those to the real backend. Without this rewrite the calls hit
// the Next dev server itself and 404. The target is the Spring Boot origin.
const API_PROXY_TARGET =
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8085";

const nextConfig: NextConfig = {
  experimental: {
    optimizePackageImports: ["lucide-react"],
  },
  turbopack: {},
  async rewrites() {
    return [
      {
        source: "/api/v1/:path*",
        destination: `${API_PROXY_TARGET}/api/v1/:path*`,
      },
    ];
  },
};

export default nextConfig;
