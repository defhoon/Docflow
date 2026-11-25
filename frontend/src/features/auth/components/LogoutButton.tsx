"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { IconButton } from "@/components/ui/IconButton";
import { LogoutIcon } from "@/components/ui/icons";

import { useAuth } from "../AuthProvider";

/** `compact`: 작업 공간 레일용 아이콘 버튼(이름·툴팁 포함). */
export function LogoutButton({ compact = false }: { compact?: boolean }) {
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

  if (compact) {
    return (
      <div className="flex flex-col items-center gap-1">
        <IconButton
          label={pending ? "로그아웃 중..." : failed ? "로그아웃 실패 · 다시 시도" : "로그아웃"}
          icon={<LogoutIcon />}
          tooltipSide="right"
          onClick={() => void handleClick()}
          disabled={pending}
          className={failed ? "text-danger-700" : undefined}
        />
      </div>
    );
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
