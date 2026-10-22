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
    <div className="flex items-center gap-2">
      <button
        type="button"
        onClick={() => void handleClick()}
        disabled={pending}
        className="rounded border border-zinc-300 px-3 py-1 text-sm disabled:opacity-50"
      >
        {pending ? "로그아웃 중..." : "로그아웃"}
      </button>
      {failed && (
        <span role="alert" className="text-sm text-red-700">
          로그아웃하지 못했습니다. 다시 시도해 주세요.
        </span>
      )}
    </div>
  );
}
