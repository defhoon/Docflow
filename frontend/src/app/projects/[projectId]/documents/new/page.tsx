import type { Metadata } from "next";
import { Suspense } from "react";

import { RequireAuth } from "@/features/auth/components/RequireAuth";
import { DocumentCreateForm } from "@/features/documents/components/DocumentCreateForm";
import { ProjectBackLink } from "@/features/documents/components/ProjectBackLink";

export const metadata: Metadata = {
  title: "문서 등록 | DocFlow",
};

export default function NewDocumentPage() {
  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-6 px-6 py-16">
      {/* 동적 세그먼트의 useParams·usePathname은 요청 시점까지 suspend되므로 하위 트리 전체를 감싼다 (SPEC-01 §8.2). */}
      <Suspense fallback={<p role="status" className="text-zinc-500">불러오는 중...</p>}>
        <ProjectBackLink />
        <RequireAuth>
          <h1 className="text-3xl font-semibold tracking-tight">문서 등록</h1>
          <DocumentCreateForm />
        </RequireAuth>
      </Suspense>
    </main>
  );
}
