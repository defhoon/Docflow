import type { Metadata } from "next";
import { Suspense } from "react";

import { LoginForm } from "@/features/auth/components/LoginForm";

export const metadata: Metadata = {
  title: "로그인 | DocFlow",
};

export default function LoginPage() {
  return (
    <main className="mx-auto flex w-full max-w-sm flex-1 flex-col gap-6 px-6 py-16">
      <header className="flex flex-col gap-2">
        <h1 className="text-3xl font-semibold tracking-tight">DocFlow</h1>
        <p className="text-zinc-600">로그인</p>
      </header>
      {/* LoginForm은 useSearchParams를 사용하므로 Suspense 경계가 필요하다. */}
      <Suspense fallback={<p role="status" className="text-zinc-500">불러오는 중...</p>}>
        <LoginForm />
      </Suspense>
    </main>
  );
}
