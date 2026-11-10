"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { useAuth } from "../AuthProvider";

export function LogoutButton() {
  const { signOut } = useAuth();
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [failed, setFailed] = useState(false);

  async function handleClick() {
    setPending(true);
    setFailed(false);
    try {
      await signOut();
      router.replace("/login");
    } catch {
      setFailed(true);
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="flex flex-col gap-1">
      <button
        type="button"
        onClick={() => void handleClick()}
        disabled={pending}
        className="w-full rounded-md px-2 py-1.5 text-left text-sm text-ink-muted hover:bg-subtle hover:text-ink disabled:opacity-50"
      >
        {pending ? "로그아웃 중..." : "로그아웃"}
      </button>
      {failed && (
        <span role="alert" className="text-xs text-danger-700">
          로그아웃하지 못했습니다. 다시 시도해 주세요.
        </span>
      )}
    </div>
  );
}
