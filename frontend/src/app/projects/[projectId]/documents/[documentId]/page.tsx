import type { Metadata } from "next";
import { Suspense } from "react";

import { AppShell } from "@/components/app-shell/AppShell";
import { ShellFallback } from "@/components/app-shell/ShellFallback";
import { DocumentDetail } from "@/features/documents/components/DocumentDetail";

export const metadata: Metadata = {
  title: "문서 | DocFlow",
};

export default function DocumentDetailPage() {
  return (
    // 동적 세그먼트의 useParams·usePathname은 요청 시점까지 suspend되므로 하위 트리 전체를 감싼다 (SPEC-01 §8.2).
    <Suspense fallback={<ShellFallback />}>
      <AppShell>
        <DocumentDetail />
      </AppShell>
    </Suspense>
  );
}
