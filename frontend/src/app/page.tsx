import { Suspense } from "react";

import { AppShell } from "@/components/app-shell/AppShell";
import { ShellFallback } from "@/components/app-shell/ShellFallback";
import { HomeDashboard } from "@/features/home/HomeDashboard";

export default function Home() {
  return (
    <Suspense fallback={<ShellFallback />}>
      <AppShell>
        <HomeDashboard />
      </AppShell>
    </Suspense>
  );
}
