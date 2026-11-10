import type { Metadata } from "next";
import { Suspense } from "react";

import { AppShell } from "@/components/app-shell/AppShell";
import { ShellFallback } from "@/components/app-shell/ShellFallback";
import { ProjectDetail } from "@/features/projects/components/ProjectDetail";

export const metadata: Metadata = {
  title: "프로젝트 | DocFlow",
};

export default function ProjectDetailPage() {
  return (
    // cacheComponents 사용 시 동적 세그먼트의 useParams·usePathname은 요청 시점까지 suspend되므로 셸 전체를 감싼다.
    <Suspense fallback={<ShellFallback />}>
      <AppShell>
        <ProjectDetail />
      </AppShell>
    </Suspense>
  );
}
