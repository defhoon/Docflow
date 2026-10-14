import { BackendHealthStatus } from "@/features/backend-health/components/BackendHealthStatus";

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-6 px-6 py-16">
      <header className="flex flex-col gap-2">
        <h1 className="text-3xl font-semibold tracking-tight">DocFlow</h1>
        <p className="text-zinc-600">Document Review Workflow — 개발 환경 연결 확인</p>
      </header>
      <section className="rounded-lg border border-zinc-200 p-4">
        <h2 className="mb-2 text-sm font-semibold text-zinc-500">Backend 연결 상태</h2>
        <BackendHealthStatus />
      </section>
    </main>
  );
}
