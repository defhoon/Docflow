import type { Metadata } from "next";
import { Suspense } from "react";

import { AppShell } from "@/components/app-shell/AppShell";
import { ShellFallback } from "@/components/app-shell/ShellFallback";
import { VersionCompare } from "@/features/compare/components/VersionCompare";

export const metadata: Metadata = {
  title: "버전 비교 | DocFlow",
};

export default function VersionComparePage() {
  return (
    // useParams·useSearchParams는 요청 시점까지 suspend되므로 하위 트리 전체를 감싼다.
    <Suspense fallback={<ShellFallback mode="focus" />}>
      <AppShell mode="focus">
        <VersionCompare />
      </AppShell>
    </Suspense>
  );
}
