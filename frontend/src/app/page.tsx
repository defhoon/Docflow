import { CurrentUserPanel } from "@/features/auth/components/CurrentUserPanel";
import { RequireAuth } from "@/features/auth/components/RequireAuth";
import { BackendHealthStatus } from "@/features/backend-health/components/BackendHealthStatus";

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-6 px-6 py-16">
      <header className="flex flex-col gap-2">
        <h1 className="text-3xl font-semibold tracking-tight">DocFlow</h1>
        <p className="text-zinc-600">Document Review Workflow</p>
      </header>
      <RequireAuth>
        <section className="rounded-lg border border-zinc-200 p-4">
          <h2 className="mb-2 text-sm font-semibold text-zinc-500">로그인 사용자</h2>
          <CurrentUserPanel />
        </section>
        <section className="mt-6 rounded-lg border border-zinc-200 p-4">
          <h2 className="mb-2 text-sm font-semibold text-zinc-500">Backend 연결 상태</h2>
          <BackendHealthStatus />
        </section>
      </RequireAuth>
    </main>
  );
}
