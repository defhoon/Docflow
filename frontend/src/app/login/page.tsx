import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";

import { AuthLayout } from "@/components/app-shell/AuthLayout";
import { LoginForm } from "@/features/auth/components/LoginForm";
import { SignupNotice } from "@/features/auth/components/SignupNotice";

export const metadata: Metadata = {
  title: "로그인 | DocFlow",
};

export default function LoginPage() {
  return (
    <AuthLayout title="로그인" subtitle="DocFlow 계정으로 로그인하세요.">
      {/* LoginForm은 useSearchParams를 사용하므로 Suspense 경계가 필요하다. */}
      <Suspense fallback={<p role="status" className="text-sm text-ink-muted">불러오는 중...</p>}>
        <SignupNotice />
        <LoginForm />
      </Suspense>
      <p className="text-center text-sm text-ink-muted">
        초대코드를 받으셨나요?{" "}
        <Link href="/signup" className="font-medium text-brand-700 hover:underline">
          회원가입
        </Link>
      </p>
    </AuthLayout>
  );
}
