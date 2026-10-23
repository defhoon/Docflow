import type { Metadata } from "next";
import Link from "next/link";

import { RequireAuth } from "@/features/auth/components/RequireAuth";
import { ProjectCreateForm } from "@/features/projects/components/ProjectCreateForm";

export const metadata: Metadata = {
  title: "새 프로젝트 | DocFlow",
};

export default function NewProjectPage() {
  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-6 px-6 py-16">
      <Link href="/projects" className="text-sm text-zinc-600 underline">
        프로젝트 목록으로
      </Link>
      <RequireAuth>
        <h1 className="mb-6 text-3xl font-semibold tracking-tight">새 프로젝트</h1>
        <ProjectCreateForm />
      </RequireAuth>
    </main>
  );
}
