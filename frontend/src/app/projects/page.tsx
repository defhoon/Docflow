import type { Metadata } from "next";
import Link from "next/link";

import { RequireAuth } from "@/features/auth/components/RequireAuth";
import { NewProjectLink, ProjectList } from "@/features/projects/components/ProjectList";

export const metadata: Metadata = {
  title: "내 프로젝트 | DocFlow",
};

export default function ProjectsPage() {
  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-6 px-6 py-16">
      <Link href="/" className="text-sm text-zinc-600 underline">
        DocFlow 홈
      </Link>
      <RequireAuth>
        <header className="flex items-center justify-between gap-4">
          <h1 className="text-3xl font-semibold tracking-tight">내 프로젝트</h1>
          <NewProjectLink />
        </header>
        <div className="mt-6">
          <ProjectList />
        </div>
      </RequireAuth>
    </main>
  );
}
