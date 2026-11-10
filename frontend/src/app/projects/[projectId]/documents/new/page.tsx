import type { Metadata } from "next";
import { Suspense } from "react";

import { AppShell } from "@/components/app-shell/AppShell";
import { ShellFallback } from "@/components/app-shell/ShellFallback";
import { Card, CardBody } from "@/components/ui/Card";
import { DocumentCreateForm } from "@/features/documents/components/DocumentCreateForm";
import { ProjectBackLink } from "@/features/documents/components/ProjectBackLink";

export const metadata: Metadata = {
  title: "문서 등록 | DocFlow",
};

export default function NewDocumentPage() {
  return (
    // 동적 세그먼트의 useParams·usePathname은 요청 시점까지 suspend되므로 하위 트리 전체를 감싼다 (SPEC-01 §8.2).
    <Suspense fallback={<ShellFallback />}>
      <AppShell>
        <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
          <header className="flex flex-col gap-2">
            <ProjectBackLink />
            <h1 className="text-xl font-semibold tracking-tight sm:text-2xl">문서 등록</h1>
            <p className="text-sm text-ink-muted">첫 파일이 v1이 됩니다. 이후 모든 구성원이 새 버전을 올리고 버전별 피드백을 남길 수 있습니다.</p>
          </header>
          <Card>
            <CardBody className="py-6">
              <DocumentCreateForm />
            </CardBody>
          </Card>
        </div>
      </AppShell>
    </Suspense>
  );
}
