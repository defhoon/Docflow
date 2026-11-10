import type { ReactNode } from "react";

import { Button } from "./Button";
import { cn } from "./cn";

export function EmptyState({ title, description, action, icon }: {
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 rounded-[var(--radius-card)] border border-dashed border-line-strong bg-surface px-6 py-10 text-center">
      {icon && <div className="mb-1 text-ink-faint">{icon}</div>}
      <p className="text-sm font-medium text-ink">{title}</p>
      {description && <p className="max-w-sm text-sm text-ink-muted">{description}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

export function ErrorState({ title, message, onRetry, retryLabel = "다시 시도", role = "alert" }: {
  title: string;
  message?: string;
  onRetry?: () => void;
  retryLabel?: string;
  role?: "alert" | "status";
}) {
  return (
    <div role={role} className="flex flex-col items-start gap-2 rounded-lg border border-danger-600/20 bg-danger-50 px-4 py-3 text-sm text-danger-700">
      <p className="font-medium">{title}</p>
      {message && <p>{message}</p>}
      {onRetry && (
        <Button size="sm" onClick={onRetry}>
          {retryLabel}
        </Button>
      )}
    </div>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden="true" className={cn("animate-pulse rounded-md bg-subtle", className)} />;
}

/** 로딩 표시: 스크린 리더에는 문구를, 화면에는 골격을 보여 준다. */
export function LoadingBlock({ label = "불러오는 중...", lines = 3 }: { label?: string; lines?: number }) {
  return (
    <div className="flex flex-col gap-2">
      <p role="status" className="sr-only">
        {label}
      </p>
      {Array.from({ length: lines }, (_, index) => (
        <Skeleton key={index} className={cn("h-4", index === lines - 1 ? "w-2/3" : "w-full")} />
      ))}
    </div>
  );
}
