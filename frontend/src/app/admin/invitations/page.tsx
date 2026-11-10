import type { Metadata } from "next";
import { Suspense } from "react";

import { AppShell } from "@/components/app-shell/AppShell";
import { ShellFallback } from "@/components/app-shell/ShellFallback";
import { InvitationAdmin } from "@/features/admin/InvitationAdmin";

export const metadata: Metadata = {
  title: "초대 관리 | DocFlow",
};

export default function InvitationAdminPage() {
  return (
    <Suspense fallback={<ShellFallback />}>
      <AppShell>
        <InvitationAdmin />
      </AppShell>
    </Suspense>
  );
}
