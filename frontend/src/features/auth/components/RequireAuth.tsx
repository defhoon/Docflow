"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";

import { useAuth } from "../AuthProvider";

/**
 * 보호 화면 래퍼. 화면 보호는 사용성 목적이며 실제 접근 제어는 Backend가 담당한다.
 */
export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { status, sessionExpired, refresh } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    if (status === "anonymous") {
      const params = new URLSearchParams({ next: pathname || "/" });
      if (sessionExpired) {
        params.set("reason", "expired");
      }
      router.replace(`/login?${params.toString()}`);
    }
  }, [status, sessionExpired, pathname, router]);

  if (status === "authenticated") {
    return <>{children}</>;
  }
  if (status === "error") {
    return (
      <div role="alert" className="m-6 flex flex-col items-start gap-2 rounded-lg border border-danger-600/20 bg-danger-50 px-4 py-3 text-sm text-danger-700">
        <p>서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.</p>
        <button
          type="button"
          onClick={() => void refresh()}
          className="rounded-md border border-line-strong bg-surface px-3 py-1 text-sm text-ink"
        >
          다시 시도
        </button>
      </div>
    );
  }
  return (
    <p role="status" className="p-6 text-sm text-ink-muted">
      로그인 상태를 확인하는 중입니다...
    </p>
  );
}
