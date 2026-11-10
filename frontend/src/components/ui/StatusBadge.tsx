import type { ReactNode } from "react";

import { cn } from "./cn";

type Tone = "neutral" | "info" | "success" | "warning" | "danger" | "brand";

const TONES: Record<Tone, string> = {
  neutral: "bg-subtle text-ink-muted ring-line",
  info: "bg-info-50 text-info-700 ring-info-600/20",
  success: "bg-success-50 text-success-700 ring-success-600/20",
  warning: "bg-warning-50 text-warning-700 ring-warning-700/20",
  danger: "bg-danger-50 text-danger-700 ring-danger-600/20",
  brand: "bg-brand-50 text-brand-700 ring-brand-500/20",
};

export function Badge({ tone = "neutral", children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset",
        TONES[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}

/** 문서 협업 상태 배지 (SPEC-07). 색만으로 구분하지 않도록 점과 텍스트를 함께 표시한다. */
export function StatusBadge({ status }: { status: "ACTIVE" | "FINALIZED" }) {
  const finalized = status === "FINALIZED";
  return (
    <Badge tone={finalized ? "success" : "info"}>
      <span aria-hidden="true" className={cn("size-1.5 rounded-full", finalized ? "bg-success-600" : "bg-info-600")} />
      {finalized ? "최종 확정" : "협업 중"}
    </Badge>
  );
}
