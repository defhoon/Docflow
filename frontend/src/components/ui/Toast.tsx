"use client";

import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from "react";

import { cn } from "./cn";

type ToastTone = "success" | "error" | "info";
type ToastItem = { id: number; tone: ToastTone; message: string };

const ToastContext = createContext<((message: string, tone?: ToastTone) => void) | null>(null);

const DISPLAY_MS = 4000;

/** 짧은 결과 알림. 화면 오른쪽 아래(모바일은 아래)에 표시하고 스크린 리더에는 polite로 알린다. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => setItems((current) => current.filter((item) => item.id !== id)), []);
  const show = useCallback(
    (message: string, tone: ToastTone = "success") => {
      const id = nextId.current++;
      setItems((current) => [...current.slice(-2), { id, tone, message }]);
      window.setTimeout(() => dismiss(id), DISPLAY_MS);
    },
    [dismiss],
  );
  const value = useMemo(() => show, [show]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed inset-x-4 bottom-4 z-[60] flex flex-col items-center gap-2 sm:inset-x-auto sm:right-6 sm:items-end">
        {items.map((item) => (
          <div
            key={item.id}
            className={cn(
              "pointer-events-auto flex max-w-sm items-start gap-3 rounded-lg px-4 py-3 text-sm text-white shadow-[var(--shadow-overlay)]",
              item.tone === "success" && "bg-ink",
              item.tone === "error" && "bg-danger-700",
              item.tone === "info" && "bg-info-700",
            )}
          >
            <span className="flex-1">{item.message}</span>
            <button type="button" onClick={() => dismiss(item.id)} className="text-white/70 hover:text-white" aria-label="알림 닫기">
              ✕
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

/** Provider 밖(단위 테스트 등)에서는 아무것도 하지 않는다. */
export function useToast(): (message: string, tone?: ToastTone) => void {
  return useContext(ToastContext) ?? noop;
}

function noop() {}
