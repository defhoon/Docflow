import type { Metadata } from "next";
import { Suspense } from "react";

import { AppShell } from "@/components/app-shell/AppShell";
import { ShellFallback } from "@/components/app-shell/ShellFallback";
import { PageHeader } from "@/components/ui/PageHeader";
import { NewProjectLink, ProjectList } from "@/features/projects/components/ProjectList";

export const metadata: Metadata = {
  title: "내 프로젝트 | DocFlow",
};

export default function ProjectsPage() {
  return (
    <Suspense fallback={<ShellFallback />}>
      <AppShell>
        <PageHeader title="내 프로젝트" description="오너이거나 구성원으로 참여 중인 프로젝트입니다." actions={<NewProjectLink />} />
        <ProjectList />
      </AppShell>
    </Suspense>
  );
}
