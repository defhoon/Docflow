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
      <div role="alert" className="flex flex-col items-start gap-2 text-red-700">
        <p>서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.</p>
        <button
          type="button"
          onClick={() => void refresh()}
          className="rounded border border-zinc-300 px-3 py-1 text-sm text-zinc-800"
        >
          다시 시도
        </button>
      </div>
    );
  }
  return (
    <p role="status" className="text-zinc-500">
      로그인 상태를 확인하는 중입니다...
    </p>
  );
}
