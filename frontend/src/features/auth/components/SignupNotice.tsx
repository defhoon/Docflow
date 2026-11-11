"use client";

import { useSearchParams } from "next/navigation";

/** 가입 직후 로그인 화면 안내. */
export function SignupNotice() {
  const searchParams = useSearchParams();
  if (searchParams.get("signup") !== "done") {
    return null;
  }
  return (
    <p role="status" className="rounded-lg bg-success-50 px-3 py-2 text-sm text-success-700">
      가입이 완료되었습니다. 로그인해 주세요.
    </p>
  );
}
