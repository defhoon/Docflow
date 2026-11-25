import type { Metadata } from "next";
import { Suspense } from "react";

import { AppShell } from "@/components/app-shell/AppShell";
import { ShellFallback } from "@/components/app-shell/ShellFallback";
import { ActivityPage } from "@/features/activity/components/ActivityPage";

export const metadata: Metadata = {
  title: "전체 활동 | DocFlow",
};

export default function AllActivityPage() {
  return (
    <Suspense fallback={<ShellFallback />}>
      <AppShell>
        <ActivityPage />
      </AppShell>
    </Suspense>
  );
}
