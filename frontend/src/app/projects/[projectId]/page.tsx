import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";

import { RequireAuth } from "@/features/auth/components/RequireAuth";
import { ProjectDetail } from "@/features/projects/components/ProjectDetail";

export const metadata: Metadata = {
  title: "프로젝트 | DocFlow",
};

export default function ProjectDetailPage() {
  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-6 px-6 py-16">
      <Link href="/projects" className="text-sm text-zinc-600 underline">
        프로젝트 목록으로
      </Link>
      {/*
        cacheComponents 사용 시 동적 세그먼트에서는 useParams(ProjectDetail)뿐 아니라
        usePathname(RequireAuth)도 요청 시점까지 suspend되므로 RequireAuth를 포함한 하위 트리 전체를 감싼다.
      */}
      <Suspense fallback={<p role="status" className="text-zinc-500">불러오는 중...</p>}>
        <RequireAuth>
          <ProjectDetail />
        </RequireAuth>
      </Suspense>
    </main>
  );
}
