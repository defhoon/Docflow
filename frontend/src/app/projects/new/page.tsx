import type { Metadata } from "next";
import { Suspense } from "react";

import { AppShell } from "@/components/app-shell/AppShell";
import { ShellFallback } from "@/components/app-shell/ShellFallback";
import { Card, CardBody } from "@/components/ui/Card";
import { PageHeader } from "@/components/ui/PageHeader";
import { ProjectCreateForm } from "@/features/projects/components/ProjectCreateForm";

export const metadata: Metadata = {
  title: "새 프로젝트 | DocFlow",
};

export default function NewProjectPage() {
  return (
    <Suspense fallback={<ShellFallback />}>
      <AppShell>
        <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
          <PageHeader
            breadcrumb={[{ label: "프로젝트", href: "/projects" }, { label: "새 프로젝트" }]}
            title="새 프로젝트"
            description="프로젝트를 만든 사람이 오너가 되며, 구성원 관리와 문서 최종 확정을 맡습니다."
          />
          <Card>
            <CardBody className="py-6">
              <ProjectCreateForm />
            </CardBody>
          </Card>
        </div>
      </AppShell>
    </Suspense>
  );
}
